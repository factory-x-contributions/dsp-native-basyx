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

package org.factoryx.dspnativebasyx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.digitaltwin.basyx.aasservice.backend.AasBackend;
import org.eclipse.digitaltwin.basyx.authorization.rbac.RbacStorage;
import org.eclipse.digitaltwin.basyx.submodelservice.backend.SubmodelBackend;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This Rest Controller is using BaSyx Services in order to allow inspecting them directly at runtime.
 */
@RestController
@Slf4j
public class DevTestController {

    private final AasBackend aasBackend;
    private final SubmodelBackend submodelBackend;
    private final RbacStorage rbacStorage;
    private final static ObjectMapper MAPPER = new ObjectMapper();

    public DevTestController(AasBackend aasBackend, SubmodelBackend submodelBackend, RbacStorage rbacStorage) {
        this.aasBackend = aasBackend;
        this.submodelBackend = submodelBackend;
        this.rbacStorage = rbacStorage;
    }

    @GetMapping(path = "/all_aas", produces = { "application/json" })
    public String getAllAas() {
        log.info("serving request for GET /all_aas");
        var array = MAPPER.createArrayNode();
        aasBackend.findAll().forEach(aas -> {
            try {
                array.add(MAPPER.readTree(MAPPER.writeValueAsString(aas)));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        return array.toString();
    }

    @GetMapping(path = "/all_submodels", produces = { "application/json" })
    public String getAllSubmodels() {
        log.info("serving request for GET /all_submodels");
        var array = MAPPER.createArrayNode();
        submodelBackend.findAll().forEach(submodel -> {
            try {
                array.add(MAPPER.readTree(MAPPER.writeValueAsString(submodel)));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        return array.toString();
    }

    @GetMapping(path = "/all_rules", produces = { "application/json" })
    public String getAllRules() {
        log.info("serving request for GET /all_rules");
        var array = MAPPER.createArrayNode();
        rbacStorage.getRbacRules().forEach((key, value) -> {
            try {
                array.add(MAPPER.readTree(MAPPER.writeValueAsString(value)));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        return array.toString();
    }
}
