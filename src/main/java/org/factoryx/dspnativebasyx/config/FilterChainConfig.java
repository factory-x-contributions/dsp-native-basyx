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

package org.factoryx.dspnativebasyx.config;

import lombok.extern.slf4j.Slf4j;
import org.factoryx.library.connector.embedded.provider.service.helpers.EnvService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URI;
import java.util.Arrays;

@Configuration
@Slf4j
public class FilterChainConfig {

    @Bean
    public SecurityFilterChain openSecurityFilterchain(HttpSecurity http, EnvService envService) throws Exception {
        String dspPath = URI.create(envService.getOwnDspUrl()).getPath() + "/**";
        String[] openPaths = {dspPath};

        log.info("Initializing Open SecurityFilterChain for dsp-protocol-lib: {}", Arrays.toString(openPaths));

        http.securityMatcher(openPaths);

        http.sessionManagement(sessions -> {
            sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        }).csrf(AbstractHttpConfigurer::disable);

        http.authorizeHttpRequests(requests -> {
            requests.requestMatchers(openPaths).permitAll();
        });

        return http.build();
    }
}
