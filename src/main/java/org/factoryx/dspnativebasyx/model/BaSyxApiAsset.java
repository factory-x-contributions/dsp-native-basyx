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

import org.factoryx.library.connector.embedded.provider.interfaces.ApiAsset;

import java.util.Map;
import java.util.UUID;

public class BaSyxApiAsset implements ApiAsset {

    private final UUID apiAssetId = UUID.randomUUID();
    @Override
    public UUID getId() {
        return apiAssetId;
    }

    @Override
    public Map<String, String> getProperties() {
        return Map.of("modelType", "BaSyxApiAsset",
                "dto-type", getContentType());
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public byte[] getDtoRepresentation() {
        return "{}".getBytes();
    }
}
