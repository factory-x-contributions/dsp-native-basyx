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

package org.factoryx.dspnativebasyx.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.digitaltwin.aas4j.v3.model.Referable;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.factoryx.library.connector.embedded.provider.interfaces.DataAsset;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SubmodelDataAsset implements DataAsset {
    private final Submodel submodel;
    private final UUID uuid;
    private final ObjectMapper objectMapper;

    public SubmodelDataAsset(Submodel submodel, UUID uuid, ObjectMapper objectMapper) {
        this.submodel = submodel;
        this.uuid = uuid;
        this.objectMapper = objectMapper;
    }


    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public Map<String, String> getProperties() {
        return Map.of("modelType", "Submodel",
                "submodelId", submodel.getId(),
                "idShort", submodel.getIdShort(),
                "dto-type", getContentType());
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public byte[] getDtoRepresentation() {
        try {
            return objectMapper.writeValueAsBytes(submodel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getSubmodelId() {
        return submodel.getId();
    }

    public List<String> getSubmodelElements(){
        return submodel.getSubmodelElements().stream().map(Referable::getIdShort).toList();
    }

}
