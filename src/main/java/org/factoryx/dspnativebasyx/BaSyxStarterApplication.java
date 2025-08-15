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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.digitaltwin.basyx.authorization.rbac.RbacStorage;
import org.factoryx.library.connector.embedded.provider.service.helpers.EnvService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;


/**
 * Currently, we are using the InMemory-Implementations of the respective BaSyx-Services,
 * since the MongoDB-based variant of BaSyx is not working.
 * From what we can tell, we expect this to be mitigated, as soon as we have refactored the dsp-protocol-lib
 * so that it no longer uses the spring-boot-starter-data-jpa module.
 * From testing, we learned that BaSyx's own MongoDB Repositories do not get properly instantiated, when
 * additional spring-boot-starter-data modules (other than spring-boot-starter-data-mongodb) are present.
 */
@SpringBootApplication
@Slf4j
@ComponentScan(basePackages = {"org.factoryx.dspnativebasyx", "org.factoryx.library", "org.eclipse.digitaltwin.basyx"})
@EnableMongoRepositories(basePackages = {"org.factoryx.library"})
public class BaSyxStarterApplication implements CommandLineRunner {

    private final RbacStorage rbacStorage;
    private final EnvService envService;

    public BaSyxStarterApplication(RbacStorage rbacStorage, EnvService envService) {
        this.rbacStorage = rbacStorage;
        this.envService = envService;
    }

    public static void main(String[] args) {
        SpringApplication.run(BaSyxStarterApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting Basyx Starter Application");
        log.info("Own Dsp Url: {}", envService.getOwnDspUrl());

        // show at startup which rules are in place

        var rbacRules = rbacStorage.getRbacRules();
        log.info("# of rbac rules: {}", rbacRules.size());

        for (var entry : rbacRules.entrySet()) {
            log.info("rule: {}", entry.getValue());
        }
    }

}
