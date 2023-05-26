package com.inmaytide.orbit.gateway;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication(scanBasePackages = {"com.inmaytide.orbit.commons", "com.inmaytide.orbit.gateway"}, exclude = {ReactiveUserDetailsServiceAutoConfiguration.class})
public class GatewayLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayLauncher.class);

    public static void main(String[] args) {
        SpringApplication.run(GatewayLauncher.class, args);
    }

    @Bean("ipRegionSearcher")
    public Searcher searcher() throws IOException {
        try {
            return Searcher.newWithBuffer(new ClassPathResource("ip2region.xdb").getContentAsByteArray());
        } catch (Exception e) {
            LOG.error("Failed to create content cached searcher, Cause by: \n", e);
            throw e;
        }
    }

    @Bean
    public List<GroupedOpenApi> apis() {
        return new ArrayList<>();
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .specVersion(SpecVersion.V30)
                .info(
                        new Info().title("Orbit Gateway API")
                                .description("Orbit System Backend Access and Login Procedures")
                                .version("1.0.0")
                                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT"))
                )
                .externalDocs(
                        new ExternalDocumentation()
                                .description("Gateway Wiki Documentation")
                                .url("https://github.com/i-orbit/gateway")
                );
    }


}
