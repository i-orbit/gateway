package com.inmaytide.orbit.gateway.configuration;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import springfox.documentation.oas.annotations.EnableOpenApi;
import springfox.documentation.swagger.web.SwaggerResource;
import springfox.documentation.swagger.web.SwaggerResourcesProvider;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Primary
@Configuration
@EnableOpenApi
public class SwaggerAggregationConfiguration implements SwaggerResourcesProvider {

    private static final String OAS_30_URL = "/v3/api-docs";

    private final RouteLocator routeLocator;

    public SwaggerAggregationConfiguration(RouteLocator routeLocator) {
        this.routeLocator = routeLocator;
    }

    @Override
    public List<SwaggerResource> get() {
        return getInstances().stream().map(this::createSwaggerResource).collect(Collectors.toList());
    }

    private Set<String> getInstances() {
        Set<String> instances = new HashSet<>();
        routeLocator.getRoutes()
                .filter(route -> route.getUri().getHost() != null && Objects.equals(route.getUri().getScheme(), "lb"))
                .subscribe(route -> instances.add(route.getUri().getHost()));
        return instances;
    }

    private SwaggerResource createSwaggerResource(String instance) {
        String url = "/" + instance.toLowerCase() + OAS_30_URL;
        SwaggerResource resource = new SwaggerResource();
        resource.setUrl(url);
        resource.setName(instance);
        resource.setSwaggerVersion("3.0");
        return resource;
    }
}
