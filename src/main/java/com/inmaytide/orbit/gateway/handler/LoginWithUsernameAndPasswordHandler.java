package com.inmaytide.orbit.gateway.handler;

import com.inmaytide.exception.translator.ThrowableTranslator;
import com.inmaytide.exception.web.HttpResponseException;
import com.inmaytide.orbit.commons.consts.Platforms;
import com.inmaytide.orbit.commons.log.OperationLogMessageProducer;
import com.inmaytide.orbit.commons.service.uaa.AuthorizationService;
import com.inmaytide.orbit.gateway.configuration.ApplicationProperties;
import com.inmaytide.orbit.gateway.domain.AccessToken;
import com.inmaytide.orbit.gateway.domain.Credentials;
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
 * @since 2023/5/12
 */
@Component
public class LoginWithUsernameAndPasswordHandler extends AbstractAuthorizeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(LoginWithUsernameAndPasswordHandler.class);

    protected LoginWithUsernameAndPasswordHandler(AuthorizationService authorizationService, OperationLogMessageProducer producer, ApplicationProperties properties, ThrowableTranslator<HttpResponseException> throwableTranslator) {
        super(authorizationService, producer, properties, throwableTranslator);
    }

    public Mono<ServerResponse> loginWithUsernameAndPassword(@NonNull ServerRequest request) {
        return request.bodyToMono(Credentials.class)
                .doOnNext(credentials -> credentials.setPlatform(Platforms.WEB))
                .map(credentials -> login(request, credentials))
                .flatMap(token -> ok().body(BodyInserters.fromValue(new AccessToken(token.getAccessToken()))));
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
