package com.inmaytide.orbit.gateway.handler;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import com.inmaytide.exception.web.translator.HttpExceptionTranslatorDelegator;
import com.inmaytide.orbit.commons.log.OperationLogMessageProducer;
import com.inmaytide.orbit.commons.service.uaa.UserService;
import com.inmaytide.orbit.gateway.configuration.ApplicationProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * @author inmaytide
 * @since 2023/5/12
 */
@Component
public class LogoutHandler extends AbstractAuthorizeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(LogoutHandler.class);

    protected LogoutHandler(OperationLogMessageProducer producer, ApplicationProperties properties, HttpExceptionTranslatorDelegator throwableTranslator, UserService userService, ImageCaptchaApplication captchaApplication) {
        super(producer, properties, throwableTranslator, userService, captchaApplication);
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    public Mono<ServerResponse> logout(ServerRequest request) {
        String token = getAccessToken(request.exchange());
        if (StringUtils.isNotBlank(token)) {
            authorizationService.revokeToken(token);
        }
        return ServerResponse.noContent().build();
    }
}
