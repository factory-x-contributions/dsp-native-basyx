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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.digitaltwin.basyx.aasrepository.feature.authorization.AasTargetInformation;
import org.eclipse.digitaltwin.basyx.authorization.rbac.Action;
import org.eclipse.digitaltwin.basyx.authorization.rbac.RbacRule;
import org.eclipse.digitaltwin.basyx.authorization.rbac.RbacStorage;
import org.eclipse.digitaltwin.basyx.submodelrepository.feature.authorization.SubmodelTargetInformation;
import org.factoryx.dspnativebasyx.model.AasDataAsset;
import org.factoryx.dspnativebasyx.model.SubmodelDataAsset;
import org.factoryx.library.connector.embedded.provider.interfaces.DataAsset;
import org.factoryx.library.connector.embedded.provider.interfaces.DspTokenValidationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RbacDCPValidationService {

    private final List<RbacRule> aasReadAccessRulesList;
    private final List<RbacRule> submodelReadAccessRulesList;
    private final Map<String, String> credentialToRbacRoleMapping;

    public RbacDCPValidationService(RbacStorage rbacStorage, @Value("${org.factoryx.dspnativebasyx.credentialtorolemappings}") String roleMappings ) {
        this.credentialToRbacRoleMapping = new HashMap<>();
        for (String mapping : roleMappings.split(",")) {
            String[] mappingParts = mapping.split("=");
            if (mappingParts.length == 2 && !mappingParts[0].trim().isEmpty() && !mappingParts[1].trim().isEmpty()) {
                this.credentialToRbacRoleMapping.put(mappingParts[0].trim(), mappingParts[1].trim());
                log.info("Credential to RBAC role mapping initialized: {}", mapping.trim());
            } else {
                log.warn("Ignoring invalid Credential to RBAC role mapping: {}", mapping);
            }
        }

        var sortedRules = rbacStorage.getRbacRules().values().stream()
                .filter(rule -> rule.getAction().contains(Action.READ) || rule.getAction().contains(Action.ALL))
                .filter(rule -> credentialToRbacRoleMapping.containsValue(rule.getRole()))
                .collect(Collectors.groupingBy(rule -> rule.getTargetInformation().getClass().getName(), Collectors.toList()));
        this.aasReadAccessRulesList = sortedRules.get(AasTargetInformation.class.getName());
        this.submodelReadAccessRulesList = sortedRules.get(SubmodelTargetInformation.class.getName());
    }

    public boolean validateReadAccessForDataAssetAndPartnerProperties(DataAsset dataAsset, Map<String, String> partnerProperties) {
        final String credentials = partnerProperties.get(DspTokenValidationService.ReservedKeys.credentials.toString());
        if (credentials == null || credentials.isEmpty()) {
            return false;
        }
        for (String credential : credentials.split(",")) {
            String mappedRole = credentialToRbacRoleMapping.get(credential.trim());
            if (dataAsset instanceof AasDataAsset aasDataAsset) {
                if (validateReadAccessForAas(aasDataAsset, mappedRole)) {
                    return true;
                }
            } else if (dataAsset instanceof SubmodelDataAsset submodelDataAsset) {
                if (validateReadAccessForSubmodel(submodelDataAsset, mappedRole)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean validateReadAccessForAas(AasDataAsset aasDataAsset, String mappedRole) {
        String targetId = aasDataAsset.getAssetId();
        if(targetId == null || targetId.isEmpty()) {
            return false;
        }
        for (RbacRule rule : aasReadAccessRulesList) {
            if(rule.getRole().equals(mappedRole)) {
                if (rule.getTargetInformation() instanceof AasTargetInformation aasTargetInformation) {
                    if (aasTargetInformation.getAasIds().contains("*") || aasTargetInformation.getAasIds().contains(targetId)) {
                        log.info("Granted access to AAS {} for role {}", targetId, mappedRole);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean validateReadAccessForSubmodel(SubmodelDataAsset submodelDataAsset, String mappedRole) {
        String targetId = submodelDataAsset.getSubmodelId();
        if(targetId == null || targetId.isEmpty()) {
            return false;
        }
        for (RbacRule rule : submodelReadAccessRulesList) {
            if(rule.getRole().equals(mappedRole)) {
                if (rule.getTargetInformation() instanceof SubmodelTargetInformation submodelTargetInformation) {
                    if ((submodelTargetInformation.getSubmodelIds().contains("*") || submodelTargetInformation.getSubmodelIds().contains(targetId))
                            && (submodelTargetInformation.getSubmodelElementIdShortPaths().contains("*")
                            || new HashSet<>(submodelTargetInformation.getSubmodelElementIdShortPaths()).containsAll(submodelDataAsset.getSubmodelElements()))) {
                        log.info("Granted access to Submodel {} for role {}", targetId, mappedRole);
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
