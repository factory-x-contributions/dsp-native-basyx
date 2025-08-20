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
import org.eclipse.digitaltwin.basyx.aasregistry.client.ApiException;
import org.eclipse.digitaltwin.basyx.aasregistry.client.model.AssetAdministrationShellDescriptor;
import org.eclipse.digitaltwin.basyx.aasregistry.main.client.factory.AasDescriptorFactory;
import org.eclipse.digitaltwin.basyx.aasregistry.main.client.mapper.AttributeMapper;
import org.eclipse.digitaltwin.basyx.aasrepository.AasRepository;
import org.eclipse.digitaltwin.basyx.aasrepository.feature.kafka.events.AasEventHandler;
import org.eclipse.digitaltwin.basyx.aasrepository.feature.mqtt.MqttAasRepositoryTopicFactory;
import org.eclipse.digitaltwin.basyx.aasrepository.feature.registry.integration.AasRepositoryRegistryLink;
import org.eclipse.digitaltwin.basyx.aasservice.backend.AasBackend;
import org.eclipse.digitaltwin.basyx.common.mqttcore.encoding.Base64URLEncoder;
import org.eclipse.digitaltwin.basyx.submodelregistry.client.factory.SubmodelDescriptorFactory;
import org.eclipse.digitaltwin.basyx.submodelregistry.client.model.SubmodelDescriptor;
import org.eclipse.digitaltwin.basyx.submodelrepository.SubmodelRepository;
import org.eclipse.digitaltwin.basyx.submodelrepository.feature.mqtt.MqttSubmodelRepositoryTopicFactory;
import org.eclipse.digitaltwin.basyx.submodelrepository.feature.registry.integration.SubmodelRepositoryRegistryLink;
import org.eclipse.digitaltwin.basyx.submodelservice.backend.SubmodelBackend;
import org.eclipse.digitaltwin.basyx.submodelservice.feature.kafka.events.SubmodelEventHandler;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation that provides access for the dsp-protocol-lib to the contents of
 * the AasBackend and SubmodelBackend from BaSyx.
 */
@Service
@Slf4j
public class BaSyxAccessManagementService implements DataAssetManagementService {

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    private final AasBackend aasBackend;
    private final SubmodelBackend submodelBackend;
    private final RbacDCPValidationService rbacDCPValidationService;
    private final ObjectMapper objectMapper;
    private final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    private final SubmodelEventHandler submodelEventHandler;
    private final AasEventHandler aasEventHandler;

    private final IMqttClient mqttClient;
    private final MqttSubmodelRepositoryTopicFactory submodelTopicFactory = new MqttSubmodelRepositoryTopicFactory(new Base64URLEncoder());
    private final MqttAasRepositoryTopicFactory aasTopicFactory = new MqttAasRepositoryTopicFactory(new Base64URLEncoder());
    private final String aasRepoName;
    private final String submodelRepoName;

    private final AasRepositoryRegistryLink aasRepositoryRegistryLink;
    private final AasDescriptorFactory aasDescriptorFactory;

    private final SubmodelRepositoryRegistryLink submodelRepositoryRegistryLink;
    private final SubmodelDescriptorFactory submodelDescriptorFactory;

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
    // currently works with UUID typed identifiers and BaSyx with Strings.
    private Map<String, UUID> basyxIdsToUuidMapping = new ConcurrentHashMap<>();
    private Map<UUID, String> submodelIdsToUuidMapping = new ConcurrentHashMap<>();
    private Map<UUID, String> aasIdsToUuidMapping = new ConcurrentHashMap<>();


    public BaSyxAccessManagementService(AasBackend aasBackend, SubmodelBackend submodelBackend,
                                        RbacDCPValidationService rbacDCPValidationService, ObjectMapper objectMapper,
                                        Optional<SubmodelEventHandler> submodelEventHandler, Optional<AasEventHandler> aasEventHandler,
                                        Optional<IMqttClient> iMqttClient, AasRepository aasRepository, SubmodelRepository submodelRepo,
                                        Optional<AasRepositoryRegistryLink> aasRepositoryRegistryLink, Optional<AttributeMapper> attributeMapper,
                                        Optional<SubmodelRepositoryRegistryLink> submodelRepositoryRegistryLink,
                                        Optional<org.eclipse.digitaltwin.basyx.submodelregistry.client.mapper.AttributeMapper> submodelAttributeMapper) {
        this.aasBackend = aasBackend;
        this.submodelBackend = submodelBackend;
        this.rbacDCPValidationService = rbacDCPValidationService;
        this.objectMapper = objectMapper;
        this.submodelEventHandler = submodelEventHandler.orElse(null);
        this.aasEventHandler = aasEventHandler.orElse(null);
        this.mqttClient = iMqttClient.orElse(null);
        this.aasRepoName = aasRepository.getName();
        this.submodelRepoName = submodelRepo.getName();
        this.aasRepositoryRegistryLink = aasRepositoryRegistryLink.orElse(null);
        if (this.aasRepositoryRegistryLink != null && attributeMapper.isPresent()) {
            this.aasDescriptorFactory = new AasDescriptorFactory(this.aasRepositoryRegistryLink.getAasRepositoryBaseURLs(), attributeMapper.get());
        } else {
            this.aasDescriptorFactory = null;
        }
        this.submodelRepositoryRegistryLink = submodelRepositoryRegistryLink.orElse(null);
        if (this.submodelRepositoryRegistryLink != null && submodelAttributeMapper.isPresent()) {
            this.submodelDescriptorFactory = new SubmodelDescriptorFactory(this.submodelRepositoryRegistryLink.getSubmodelRepositoryBaseURLs(), submodelAttributeMapper.get());
        } else {
            this.submodelDescriptorFactory = null;
        }
    }


    @Override
    public DataAsset getById(UUID id) {
        if (SHELLS_API_ASSET_ID.equals(id)) {
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
        if (SHELLS_API_ASSET_ID.equals(id)) {
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
        if ((path.startsWith("/shells") && !SHELLS_API_ASSET_ID.equals(apiAssetId) ||
                (path.startsWith("/submodels") && !SUBMODELS_API_ASSET_ID.equals(apiAssetId)))) {
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
                        if (submodelEventHandler != null) {
                            final Submodel finalSubmodel = submodel;
                            executorService.submit(() -> submodelEventHandler.onSubmodelCreated(finalSubmodel));
                        }
                        if (mqttClient != null) {
                            executorService.submit(() -> sendMqttMessage(submodelTopicFactory.createCreateSubmodelTopic(submodelRepoName), new String(requestBody)));
                        }
                        if (submodelDescriptorFactory != null) {
                            final Submodel finalSubmodel = submodel;
                            executorService.submit(() -> registerSubmodel(finalSubmodel));
                        }
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
                        if (aasEventHandler != null) {
                            final AssetAdministrationShell finalShell = shell;
                            executorService.submit(() -> aasEventHandler.onAasCreated(finalShell));
                        }
                        if (mqttClient != null) {
                            executorService.submit(() -> sendMqttMessage(aasTopicFactory.createCreateAASTopic(aasRepoName), new String(requestBody)));
                        }
                        if (aasDescriptorFactory != null) {
                            final AssetAdministrationShell finalShell = shell;
                            executorService.submit(() -> registerAas(finalShell));
                        }
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
                            if (submodelEventHandler != null) {
                                final Submodel finalSubmodel = submodel;
                                executorService.submit(() -> submodelEventHandler.onSubmodelUpdated(finalSubmodel));
                            }
                            if (mqttClient != null) {
                                executorService.submit(() -> sendMqttMessage(submodelTopicFactory.createUpdateSubmodelTopic(submodelRepoName), new String(requestBody)));
                            }
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
                            if (aasEventHandler != null) {
                                final AssetAdministrationShell finalShell = shell;
                                executorService.submit(() -> aasEventHandler.onAasUpdated(shellId, finalShell));
                            }
                            if (mqttClient != null) {
                                executorService.submit(() -> sendMqttMessage(aasTopicFactory.createUpdateAASTopic(aasRepoName), new String(requestBody)));
                            }
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
                                if (submodelEventHandler != null) {
                                    executorService.submit(() -> submodelEventHandler.onSubmodelDeleted(submodelId));
                                }
                                if (mqttClient != null) {
                                    String serializedSubmodel = objectMapper.writeValueAsString(opt.get());
                                    executorService.submit(() -> sendMqttMessage(submodelTopicFactory.createDeleteSubmodelTopic(submodelRepoName), serializedSubmodel));
                                }
                                if (submodelRepositoryRegistryLink != null) {
                                    executorService.submit(() -> {
                                        try {
                                            submodelRepositoryRegistryLink.getRegistryApi().deleteSubmodelDescriptorById(submodelId);
                                        } catch (org.eclipse.digitaltwin.basyx.submodelregistry.client.ApiException e) {
                                            log.error("Error registering submodel deletion {}", submodelId, e);
                                        }
                                    });
                                }
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
                                if (aasEventHandler != null) {
                                    executorService.submit(() -> aasEventHandler.onAasDeleted(shellId));
                                }
                                if (mqttClient != null) {
                                    String serializedShell = objectMapper.writeValueAsString(opt.get());
                                    executorService.submit(() -> sendMqttMessage(aasTopicFactory.createDeleteAASTopic(aasRepoName), serializedShell));
                                }
                                if (aasRepositoryRegistryLink != null) {
                                    executorService.submit(() -> {
                                        try {
                                            aasRepositoryRegistryLink.getRegistryApi().deleteAssetAdministrationShellDescriptorById(shellId);
                                        } catch (ApiException e) {
                                            log.error("Error registering shell deletion {}", shellId, e);
                                        }
                                    });
                                }
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


    private void sendMqttMessage(String topic, String payload) {
        try {
            MqttMessage msg = payload == null ? new MqttMessage() : new MqttMessage(payload.getBytes());
            mqttClient.publish(topic, msg);
            log.info("Sent MQTT message about topic {} with payload: {}", topic, payload);
        } catch (MqttPersistenceException e) {
            log.error("Could not persist mqtt message", e);
        } catch (MqttException e) {
            log.error("Could not send mqtt message", e);
        }
    }

    private void registerAas(AssetAdministrationShell shell) {
        try {
            AssetAdministrationShellDescriptor descriptor = aasDescriptorFactory.create(shell);
            aasRepositoryRegistryLink.getRegistryApi().postAssetAdministrationShellDescriptor(descriptor);
            log.info("Shell '{}' has been automatically linked with the Registry", descriptor.getId());
        } catch (ApiException e) {
            log.error("Failed to automatically link shell descriptor", e);
        }
    }

    private void registerSubmodel(Submodel submodel) {
        try {
            SubmodelDescriptor descriptor = submodelDescriptorFactory.create(submodel);
            submodelRepositoryRegistryLink.getRegistryApi().postSubmodelDescriptor(descriptor);
            log.info("Submodel '{}' has been automatically linked with the Registry", descriptor.getId());
        } catch (Exception e) {
            log.error("Failed to automatically link submodel", e);
        }
    }

}
