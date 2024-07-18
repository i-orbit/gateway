package com.inmaytide.orbit.gateway.configuration;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.constant.CommonConstant;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.impl.provider.ClassPathResourceProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * @author inmaytide
 * @since 2024/7/17
 */
@Configuration
public class CaptchaResourceConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(CaptchaResourceConfiguration.class);

    private final ResourceStore resourceStore;

    public CaptchaResourceConfiguration(ResourceStore resourceStore) {
        this.resourceStore = resourceStore;
    }

    @PostConstruct
    public void init() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Stream.of(resolver.getResources("classpath*:background/*")).forEach(resource -> {
            try {
                Resource res = new Resource(ClassPathResourceProvider.NAME, "background/" + resource.getFilename(), CommonConstant.DEFAULT_TAG);
                resourceStore.addResource(CaptchaTypeConstant.SLIDER, res);
            } catch (Exception e) {
                LOG.error("资源加载失败: {}", resource.getFilename(), e);
            }
        });
    }
}
