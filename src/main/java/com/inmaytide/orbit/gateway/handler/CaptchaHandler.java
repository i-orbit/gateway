package com.inmaytide.orbit.gateway.handler;


import com.inmaytide.exception.web.BadRequestException;
import com.inmaytide.orbit.commons.utils.ValueCaches;
import com.inmaytide.orbit.gateway.configuration.ErrorCode;
import com.inmaytide.orbit.gateway.domain.Captcha;
import com.inmaytide.orbit.gateway.util.CaptchaGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

/**
 * @author inmaytide
 * @since 2022/11/26
 */
@Component
@Tag(name = "登录验证码相关接口")
public class CaptchaHandler extends AbstractHandler {

    private static final Logger log = LoggerFactory.getLogger(CaptchaHandler.class);

    private static final String CACHE_NAME_CAPTCHA = "CAPTCHA";

    @Operation(summary = "获取验证码图片")
    @ApiResponse(
            content = @Content(
                    schema = @Schema(implementation = Captcha.class)
            )
    )
    public Mono<ServerResponse> getCaptcha(@NonNull ServerRequest request) {
        String captchaKey = UUID.randomUUID().toString();
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            String captcha = CaptchaGenerator.generate(os);
            String image = Base64.getEncoder().encodeToString(os.toByteArray());
            ValueCaches.put(CACHE_NAME_CAPTCHA, captchaKey, captcha, 15, TimeUnit.MINUTES);
            return ok().body(Mono.just(new Captcha(captchaKey, image)), Captcha.class);
        } catch (IOException e) {
            log.error("An error occurred while generating the captcha, Cause by: ", e);
            throw new BadRequestException(ErrorCode.E_0x00200008);
        }
    }

    public boolean validate(String captchaKey, String captchaValue) {
        return ValueCaches.getAndDelete(CACHE_NAME_CAPTCHA, captchaKey)
                .map(value -> StringUtils.equalsIgnoreCase(value, captchaValue))
                .orElse(false);
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
}
