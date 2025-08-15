/*
 * Copyright (c) 2025. Fraunhofer-Gesellschaft zur Foerderung der angewandten Forschung e.V. (represented by Fraunhofer ISST)
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.factoryx.dspnativebasyx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultAssetAdministrationShell;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodel;
import org.eclipse.digitaltwin.basyx.aasservice.backend.AasBackend;
import org.eclipse.digitaltwin.basyx.submodelservice.backend.SubmodelBackend;
import org.factoryx.dspnativebasyx.model.AasDataAsset;
import org.factoryx.dspnativebasyx.model.BaSyxApiAsset;
import org.factoryx.dspnativebasyx.model.SubmodelDataAsset;
import org.factoryx.library.connector.embedded.provider.interfaces.DataAsset;
import org.factoryx.library.connector.embedded.provider.interfaces.DataAssetManagementService;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation that provides access for the dsp-protocol-lib to the contents of
 * the AasBackend and SubmodelBackend from BaSyx.
 */
@Service
@Slf4j
public class BaSyxAccessManagementService implements DataAssetManagementService {

    private final AasBackend aasBackend;
    private final SubmodelBackend submodelBackend;
    private final RbacDCPValidationService rbacDCPValidationService;
    private final ObjectMapper objectMapper;
    private final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    private final BaSyxApiAsset SHELLS_API_ASSET = new BaSyxApiAsset() {
        @Override
        public Map<String, String> getProperties() {
            return Map.of("modelType", "ShellsApiAsset",
                    "dto-type", getContentType());
        }
    };
    private final UUID SHELLS_API_ASSET_ID = SHELLS_API_ASSET.getId();
    private final BaSyxApiAsset SUBMODELS_API_ASSET = new BaSyxApiAsset() {
        @Override
        public Map<String, String> getProperties() {
            return Map.of("modelType", "SubmodelsApiAsset",
                    "dto-type", getContentType());
        }
    };
    private final UUID SUBMODELS_API_ASSET_ID = SUBMODELS_API_ASSET.getId();

    // provisional mapping helpers in order to cope with the fact that the dsp-protocol-lib
    // currently works with UUID typed identfiers and BaSyx with Strings.
    private Map<String, UUID> basyxIdsToUuidMapping = new ConcurrentHashMap<>();
    private Map<UUID, String> submodelIdsToUuidMapping = new ConcurrentHashMap<>();
    private Map<UUID, String> aasIdsToUuidMapping = new ConcurrentHashMap<>();


    public BaSyxAccessManagementService(AasBackend aasBackend, SubmodelBackend submodelBackend,
                                        RbacDCPValidationService rbacDCPValidationService, ObjectMapper objectMapper) {
        this.aasBackend = aasBackend;
        this.submodelBackend = submodelBackend;
        this.rbacDCPValidationService = rbacDCPValidationService;
        this.objectMapper = objectMapper;
    }


    @Override
    public DataAsset getById(UUID id) {
        if(SHELLS_API_ASSET_ID.equals(id)) {
            return SHELLS_API_ASSET;
        }
        if (SUBMODELS_API_ASSET_ID.equals(id)) {
            return SUBMODELS_API_ASSET;
        }
        if (aasIdsToUuidMapping.containsKey(id)) {
            var aasOptional = aasBackend.findById(aasIdsToUuidMapping.get(id));
            if (aasOptional.isPresent()) {
                return new AasDataAsset(aasOptional.get(), id, objectMapper);
            }
        } else if (submodelIdsToUuidMapping.containsKey(id)) {
            var submodelOptional = submodelBackend.findById(submodelIdsToUuidMapping.get(id));
            if (submodelOptional.isPresent()) {
                return new SubmodelDataAsset(submodelOptional.get(), id, objectMapper);
            }
        }
        log.warn("No such id {}", id);
        return null;
    }


    @Override
    public DataAsset getByIdForProperties(UUID id, Map<String, String> partnerProperties) {
        if(SHELLS_API_ASSET_ID.equals(id)) {
            return SHELLS_API_ASSET;
        }
        if (SUBMODELS_API_ASSET_ID.equals(id)) {
            return SUBMODELS_API_ASSET;
        }
        if (aasIdsToUuidMapping.containsKey(id)) {
            var aasOptional = aasBackend.findById(aasIdsToUuidMapping.get(id));
            if (aasOptional.isPresent()) {
                DataAsset dataAsset = new AasDataAsset(aasOptional.get(), id, objectMapper);
                if (rbacDCPValidationService.validateReadAccessForDataAssetAndPartnerProperties(dataAsset, partnerProperties)) {
                    return dataAsset;
                }
                return null;
            }
        } else if (submodelIdsToUuidMapping.containsKey(id)) {
            var submodelOptional = submodelBackend.findById(submodelIdsToUuidMapping.get(id));
            if (submodelOptional.isPresent()) {
                DataAsset dataAsset = new SubmodelDataAsset(submodelOptional.get(), id, objectMapper);
                if (rbacDCPValidationService.validateReadAccessForDataAssetAndPartnerProperties(dataAsset, partnerProperties)) {
                    return dataAsset;
                }
                return null;
            }
        }
        log.warn("No such id {}", id);
        return null;
    }

    @Override
    public List<? extends DataAsset> getAll(Map<String, String> partnerProperties) {
        ArrayList<DataAsset> dataAssets = new ArrayList<>();
        dataAssets.add(SHELLS_API_ASSET);
        dataAssets.add(SUBMODELS_API_ASSET);
        aasBackend.findAll().forEach(asset -> {
            UUID uuid = basyxIdsToUuidMapping.computeIfAbsent(asset.getId(), any -> UUID.randomUUID());
            aasIdsToUuidMapping.put(uuid, asset.getId());
            DataAsset dataAsset = new AasDataAsset(asset, uuid, objectMapper);
            if (rbacDCPValidationService.validateReadAccessForDataAssetAndPartnerProperties(dataAsset, partnerProperties)) {
                dataAssets.add(dataAsset);
            }

        });

        submodelBackend.findAll().forEach(submodel -> {
            UUID uuid = basyxIdsToUuidMapping.computeIfAbsent(submodel.getId(), any -> UUID.randomUUID());
            submodelIdsToUuidMapping.put(uuid, submodel.getId());
            DataAsset dataAsset = new SubmodelDataAsset(submodel, uuid, objectMapper);
            if (rbacDCPValidationService.validateReadAccessForDataAssetAndPartnerProperties(dataAsset, partnerProperties)) {
                dataAssets.add(dataAsset);
            }
        });

        return dataAssets;

    }

    @Override
    public ResponseEntity<byte[]> forwardToApiAsset(UUID apiAssetId, HttpMethod method, byte[] requestBody,
                                                    HttpHeaders headers, String path, MultiValueMap<String, String> incomingQueryParams) {
        log.info("Method {}", method);
        log.info("Headers {}", headers);
        log.info("Path {}", path);
        log.info("IncomingQueryParams {}", incomingQueryParams);

        if ((path.startsWith("/shells") && !SHELLS_API_ASSET_ID.equals(apiAssetId) ||
                (path.startsWith("/submodels") && !SUBMODELS_API_ASSET_ID.equals(apiAssetId)) )) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            switch (method.name()) {
                case "POST" -> {
                    if (path.equals("/submodels")) {

                        Submodel submodel = objectMapper.readValue(requestBody, DefaultSubmodel.class);
                        if (submodelBackend.findById(submodel.getId()).isPresent()) {
                            return ResponseEntity.status(HttpStatus.CONFLICT).build();
                        }
                        submodel = submodelBackend.save(submodel);
                        log.info("Created submodel {}", submodel.getId());
                        return ResponseEntity.status(HttpStatus.CREATED)
                                .contentType(MediaType.parseMediaType("application/json; charset=UTF-8"))
                                .body(objectMapper.writeValueAsBytes(submodel));
                    }

                    if (path.equals("/shells")) {
                        AssetAdministrationShell shell = objectMapper.readValue(requestBody, DefaultAssetAdministrationShell.class);
                        if (aasBackend.findById(shell.getId()).isPresent()) {
                            return ResponseEntity.status(HttpStatus.CONFLICT).build();
                        }
                        shell = aasBackend.save(shell);
                        log.info("Created shell {}", shell.getId());
                        return ResponseEntity.status(HttpStatus.CREATED)
                                .contentType(MediaType.valueOf("application/json; charset=UTF-8"))
                                .body(objectMapper.writeValueAsBytes(shell));
                    }
                }

                case "PUT" -> {
                    if (path.startsWith("/submodels")) {
                        String submodelIdB64 = path.substring("/submodels/".length());
                        String submodelId = new String(B64_DECODER.decode(submodelIdB64));
                        Submodel submodel = objectMapper.readValue(requestBody, DefaultSubmodel.class);
                        if (submodel.getId().equals(submodelId) && submodelBackend.findById(submodel.getId()).isPresent()) {
                            submodel = submodelBackend.save(submodel);
                            log.info("Updated submodel {}", submodel.getId());
                            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
                        }
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                    }

                    if (path.startsWith("/shells")) {
                        AssetAdministrationShell shell = objectMapper.readValue(requestBody, DefaultAssetAdministrationShell.class);
                        String shellIdB64 = path.substring("/shells/".length());
                        String shellId = new String(B64_DECODER.decode(shellIdB64));
                        if (shell.getId().equals(shellId) && aasBackend.findById(shell.getId()).isPresent()) {
                            shell = aasBackend.save(shell);
                            log.info("Updated shell {}", shell.getId());
                            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
                        }
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                    }
                }

                case "DELETE" -> {
                    if (path.startsWith("/submodels")) {
                        String submodelIdB64 = path.substring("/submodels/".length());
                        String submodelId = new String(B64_DECODER.decode(submodelIdB64));
                        var opt = submodelBackend.findById(submodelId);
                        if (opt.isPresent()) {
                            try {
                                submodelBackend.delete(opt.get());
                                log.info("Deleted submodel {}", submodelId);
                                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
                            } catch (Exception e) {
                                log.error("Failed to delete submodel {}", submodelId, e);
                            }
                        }
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                    }

                    if (path.startsWith("/shells")) {
                        String shellIdB64 = path.substring("/shells/".length());
                        String shellId = new String(B64_DECODER.decode(shellIdB64));
                        var opt = aasBackend.findById(shellId);
                        if (opt.isPresent()) {
                            try {
                                aasBackend.delete(opt.get());
                                log.info("Deleted shell {}", shellId);
                                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
                            } catch (Exception e) {
                                log.error("Failed to delete shell {}", shellId, e);
                            }
                        }
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to forward to api asset {}", apiAssetId, e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

}
