package com.inmaytide.orbit.gateway.configuration;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

@Primary
@Configuration
public class OpenApiAggregationConfiguration {

    private static final String OAS_30_URL = "/v3/api-docs";

    private final RouteLocator routeLocator;

    public OpenApiAggregationConfiguration(RouteLocator routeLocator) {
        this.routeLocator = routeLocator;
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

    @Bean
    public List<GroupedOpenApi> apis(SwaggerUiConfigProperties configProperties) {
        List<GroupedOpenApi> groups = new ArrayList<>();
        Set<AbstractSwaggerUiConfigProperties.SwaggerUrl> URLs = new HashSet<>();
        // gateway 本身文档
        URLs.add(new AbstractSwaggerUiConfigProperties.SwaggerUrl("gateway", OAS_30_URL, "Gateway API"));
        getInstances().forEach(service -> {
            AbstractSwaggerUiConfigProperties.SwaggerUrl URL = new AbstractSwaggerUiConfigProperties.SwaggerUrl();
            URL.setName(StringUtils.capitalize(service) + " API");
            URL.setUrl("/" + service + OAS_30_URL);
            URLs.add(URL);
        });
        configProperties.setUrls(URLs);
        return groups;
    }



    private Set<String> getInstances() {
        Set<String> instances = new HashSet<>();
        routeLocator.getRoutes()
                .filter(route -> route.getUri().getHost() != null && Objects.equals(route.getUri().getScheme(), "lb"))
                .subscribe(route -> instances.add(route.getUri().getHost()));
        return instances;
    }

}
