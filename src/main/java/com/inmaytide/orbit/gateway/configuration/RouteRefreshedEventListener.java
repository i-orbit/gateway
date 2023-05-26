package com.inmaytide.orbit.gateway.configuration;

import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.cloud.gateway.event.RefreshRoutesResultEvent;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author inmaytide
 * @since 2023/5/26
 */
@Component
public class RouteRefreshedEventListener implements ApplicationListener<RefreshRoutesResultEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(RouteRefreshedEventListener.class);

    private static final String OAS_30_URL = "/v3/api-docs";

    private final SwaggerUiConfigProperties properties;

    public RouteRefreshedEventListener(SwaggerUiConfigProperties properties) {
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(@NotNull RefreshRoutesResultEvent event) {
        if (!event.isSuccess()) {
            LOG.warn("Detected that Gateway's routes failure to refresh, Cause by: ", event.getThrowable());
            return;
        }
        if (event.getSource() instanceof RouteLocator routeLocator) {
            synchronized (properties) {
                LOG.info("Detected that Gateway's routes has been refreshed, and it is now starting to refresh the Swagger URLs");
                Set<AbstractSwaggerUiConfigProperties.SwaggerUrl> URLs = new HashSet<>();
                getInstances(routeLocator).forEach(service -> {
                    AbstractSwaggerUiConfigProperties.SwaggerUrl URL = new AbstractSwaggerUiConfigProperties.SwaggerUrl();
                    URL.setName(StringUtils.capitalize(service) + " API");
                    URL.setUrl("/" + service + OAS_30_URL);
                    URLs.add(URL);
                });
                properties.setUrls(URLs);
                LOG.info("Swagger URLs refreshed");
            }
        }
    }

    private Set<String> getInstances(RouteLocator routeLocator) {
        Set<String> instances = new HashSet<>();
        routeLocator.getRoutes()
                .filter(route -> route.getUri().getHost() != null && Objects.equals(route.getUri().getScheme(), "lb"))
                .subscribe(route -> instances.add(route.getUri().getHost()));
        return instances;
    }
}
