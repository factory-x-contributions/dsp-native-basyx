/*******************************************************************************
 * Copyright (C) 2024 the Eclipse BaSyx Authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * SPDX-License-Identifier: MIT
 ******************************************************************************/

package org.eclipse.digitaltwin.basyx.authorization;

import org.eclipse.digitaltwin.basyx.http.CorsPathPatternProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Common configurations for security
 *
 * @author danish
 */
@Configuration
@ConditionalOnExpression("#{${" + CommonAuthorizationProperties.ENABLED_PROPERTY_KEY + ":false}}")
public class CommonSecurityConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonSecurityConfiguration.class);

    /**
     * This method was refactored in way, that the security filter chain which is created here, behaves exactly like
     * in the original implementation from the BaSyx project, as long as the property
     * <p>
     * basyx.feature.authorization.narrowsecuritymatcher
     * <p>
     * is set to the default value ("false").
     * <p>
     * But if this is set to "true", then a httpSecurity matcher will be applied that makes sure, that this
     * security filter chain will only be applied to the endpoints that are related to the specific BaSyx REST api.
     *
     * @param http
     * @param narrowSecurityMatcher
     * @return
     * @throws Exception
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, List<CorsPathPatternProvider> configurationUrlProviders,
                                           @Value("${basyx.feature.authorization.narrowsecuritymatcher:false}")
                                           boolean narrowSecurityMatcher) throws Exception {
        String [] initialMatchers = {"/**"};
        if(narrowSecurityMatcher){
            Set<String> matcherSet = new HashSet<>(Set.of("/actuator/health/**", "/swagger-ui/**", "/v3/**",
                    "/api-docs/**", "/api-docs/swagger-config/**", "/description"));

            configurationUrlProviders.forEach(provider -> matcherSet.add(provider.getPathPattern()));
            initialMatchers = matcherSet.toArray(new String[0]);

            http.securityMatcher(initialMatchers);
            LOGGER.info("Narrow Security Matcher enabled for patterns: {}", matcherSet);
        }

        final String[] finalMatchers = initialMatchers;

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, finalMatchers).permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/v3/**").permitAll()
                        .requestMatchers("/api-docs/**").permitAll()
                        .requestMatchers("/api-docs/swagger-config/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/description").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtGrantedAuthoritiesConverter());

        return converter;
    }

}
