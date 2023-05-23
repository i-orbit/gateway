package com.inmaytide.orbit.gateway.handler;

import com.inmaytide.exception.translator.ThrowableTranslator;
import com.inmaytide.exception.web.HttpResponseException;
import com.inmaytide.orbit.commons.log.OperationLogMessageProducer;
import com.inmaytide.orbit.commons.service.uaa.AuthorizationService;
import com.inmaytide.orbit.commons.service.uaa.UserService;
import com.inmaytide.orbit.gateway.configuration.ApplicationProperties;
import com.inmaytide.orbit.gateway.domain.AccessToken;
import com.inmaytide.orbit.gateway.domain.Credentials;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
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
 * @since 2023/5/12
 */
@Component
@Tag(name = "通过用户名密码登录系统处理器")
public class LoginWithUsernameAndPasswordHandler extends AbstractAuthorizeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(LoginWithUsernameAndPasswordHandler.class);

    protected LoginWithUsernameAndPasswordHandler(OperationLogMessageProducer producer, ApplicationProperties properties, ThrowableTranslator<HttpResponseException> throwableTranslator, UserService userService, CaptchaHandler captchaHandler) {
        super(producer, properties, throwableTranslator, userService, captchaHandler);
    }


    @Operation(summary = "通过用户名密码登录系统")
    @RequestBody(
            content = @Content(
                    schema = @Schema(implementation = Credentials.class)
            )
    )
    @ApiResponse(
            content = @Content(
                    schema = @Schema(implementation = AccessToken.class)
            )
    )
    public Mono<ServerResponse> loginWithUsernameAndPassword(@NonNull ServerRequest request) {
        return request.bodyToMono(Credentials.class)
                .map(credentials -> login(request, credentials))
                .flatMap(token -> ok().body(BodyInserters.fromValue(new AccessToken(token.getAccessToken()))));
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
