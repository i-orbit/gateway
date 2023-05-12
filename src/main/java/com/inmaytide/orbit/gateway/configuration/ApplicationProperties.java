package com.inmaytide.orbit.gateway.configuration;

import com.inmaytide.orbit.commons.configuration.CommonProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author inmaytide
 * @since 2023/5/12
 */
@Component
@ConfigurationProperties(prefix = "application")
public class ApplicationProperties extends CommonProperties {

    private List<String> disabledAccessSources;

    private List<String> enabledAccessSources;

    public List<String> getDisabledAccessSources() {
        return disabledAccessSources;
    }

    public void setDisabledAccessSources(List<String> disabledAccessSources) {
        this.disabledAccessSources = disabledAccessSources;
    }

    public List<String> getEnabledAccessSources() {
        return enabledAccessSources;
    }

    public void setEnabledAccessSources(List<String> enabledAccessSources) {
        this.enabledAccessSources = enabledAccessSources;
    }
}
