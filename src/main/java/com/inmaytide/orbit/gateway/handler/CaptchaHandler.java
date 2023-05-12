package com.inmaytide.orbit.gateway.handler;

import com.carrot.gateway.config.ErrorCode;
import com.inmaytide.exception.web.BadRequestException;
import org.apache.commons.lang3.StringUtils;
import org.patchca.service.ConfigurableCaptchaService;
import org.patchca.utils.encoder.EncoderHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.Base64Utils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

/**
 * @author inmaytide
 * @since 2022/11/26
 */
@Component
public class CaptchaHandler extends AbstractHandler {

    private static final Logger log = LoggerFactory.getLogger(CaptchaHandler.class);

    private static final String CACHE_CAPTCHA_KEY_PATTERN = "redis-captcha::%s";

    private static final String DEFAULT_IMAGE_FORMAT = "png";

    private final ConfigurableCaptchaService service;

    public CaptchaHandler(ConfigurableCaptchaService service) {
        this.service = service;
    }

    private String generateCacheName(String cacheName) {
        return String.format(CACHE_CAPTCHA_KEY_PATTERN, cacheName);
    }

    public Mono<ServerResponse> getCaptcha(@NonNull ServerRequest request) {
        String captchaKey = UUID.randomUUID().toString();
        String cacheName = generateCacheName(captchaKey);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            String captcha = EncoderHelper.getChallangeAndWriteImage(service, DEFAULT_IMAGE_FORMAT, os);
            String image = Base64Utils.encodeToString(os.toByteArray());
            stringRedisTemplate.opsForValue().set(cacheName, captcha, 15, TimeUnit.MINUTES);
            return ok().body(Mono.just(Map.of("image", image, "captchaKey", captchaKey)), Map.class);
        } catch (IOException e) {
            log.error("An error occurred while generating the captcha, Cause by: ", e);
            throw new BadRequestException(ErrorCode.E_0x000300006.getValue());
        }
    }

    public boolean validate(String captchaKey, String captchaValue) {
        String cacheName = generateCacheName(captchaKey);
        String value = stringRedisTemplate.opsForValue().getAndDelete(cacheName);
        return StringUtils.equals(value, captchaValue);
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
}
