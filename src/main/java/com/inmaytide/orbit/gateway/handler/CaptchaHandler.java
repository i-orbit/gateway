package com.inmaytide.orbit.gateway.handler;


import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.CaptchaResponse;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import com.inmaytide.orbit.gateway.domain.CaptchaValidate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

/**
 * @author inmaytide
 * @since 2022/11/26
 */
@Component
@Tag(name = "登录验证码相关接口")
public class CaptchaHandler extends AbstractHandler {

    private static final Logger log = LoggerFactory.getLogger(CaptchaHandler.class);

    private final ImageCaptchaApplication captchaApplication;

    public CaptchaHandler(ImageCaptchaApplication captchaApplication) {
        this.captchaApplication = captchaApplication;
    }

    @Operation(summary = "获取验证码信息")
    @ApiResponse(
            content = @Content(
                    schema = @Schema(implementation = CaptchaResponse.class)
            )
    )
    public Mono<ServerResponse> getCaptcha(@NonNull ServerRequest request) {
        CaptchaResponse<ImageCaptchaVO> response = captchaApplication.generateCaptcha(CaptchaTypeConstant.SLIDER);
        return ok().body(BodyInserters.fromValue(response));
    }

    @Operation(summary = "验证验证码")
    public Mono<ServerResponse> validate(@NonNull ServerRequest request) {
        return request.bodyToMono(CaptchaValidate.class)
                .map(e -> captchaApplication.matching(e.getId(), e.getData()))
                .flatMap(match -> ok().bodyValue(match));
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
}
