package com.inmaytide.orbit.gateway.handler;

import com.inmaytide.exception.translator.ThrowableTranslator;
import com.inmaytide.exception.web.HttpResponseException;
import com.inmaytide.orbit.commons.log.OperationLogMessageProducer;
import com.inmaytide.orbit.commons.service.uaa.AuthorizationService;
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

    protected LogoutHandler(OperationLogMessageProducer producer, ApplicationProperties properties, ThrowableTranslator<HttpResponseException> throwableTranslator, UserService userService, CaptchaHandler captchaHandler) {
        super(producer, properties, throwableTranslator, userService, captchaHandler);
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    public Mono<ServerResponse> logout(ServerRequest request) {
        String token = getAccessToken(request);
        if (StringUtils.isNotBlank(token)) {
            authorizationService.revokeToken(token);
        }
        return ServerResponse.noContent().build();
    }
}
