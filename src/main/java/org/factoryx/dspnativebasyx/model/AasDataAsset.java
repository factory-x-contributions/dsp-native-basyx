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
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.factoryx.library.connector.embedded.provider.interfaces.DataAsset;

import java.util.Map;
import java.util.UUID;

/**
 * This class wraps an AssetAdministrationShell object from the BaSyx world
 * and maps it into a DataAsset which can be forwarded to the dsp-protocol-lib.
 *
 */
public class AasDataAsset implements DataAsset {

    private final AssetAdministrationShell shell;
    private final UUID uuid;
    private final ObjectMapper objectMapper;

    public AasDataAsset(AssetAdministrationShell shell, UUID uuid, ObjectMapper objectMapper) {
        this.shell = shell;
        this.uuid = uuid;
        this.objectMapper = objectMapper;
    }

    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public Map<String, String> getProperties() {
        return Map.of("modelType", "AssetAdministrationShell",
                "globalAssetId", shell.getAssetInformation().getGlobalAssetId(),
                "idShort", shell.getIdShort(),
                "dto-type", getContentType());
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public byte[] getDtoRepresentation() {
        try {
            return objectMapper.writeValueAsBytes(shell);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getAssetId() {
        return shell.getAssetInformation().getGlobalAssetId();
    }
}
