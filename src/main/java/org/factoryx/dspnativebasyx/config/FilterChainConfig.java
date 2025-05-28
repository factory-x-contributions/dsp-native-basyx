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
        String[] openPaths = {dspPath, "/all_rules", "/all_aas", "/all_submodels"};

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
