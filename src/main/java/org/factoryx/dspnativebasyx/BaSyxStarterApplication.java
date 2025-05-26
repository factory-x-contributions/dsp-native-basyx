package org.factoryx.dspnativebasyx;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.digitaltwin.basyx.authorization.rbac.RbacStorage;
import org.factoryx.library.connector.embedded.provider.service.helpers.EnvService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URI;
import java.util.Arrays;


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
@EntityScan(basePackages = {"org.factoryx.library"})
@EnableJpaRepositories(basePackages = {"org.factoryx.library"})
@ComponentScan(basePackages = {"org.factoryx.dspnativebasyx", "org.factoryx.library", "org.eclipse.digitaltwin.basyx",
        "org.factoryx.library.connector.embedded.provider.controller"})
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

    @Bean
    @Order(2)
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
