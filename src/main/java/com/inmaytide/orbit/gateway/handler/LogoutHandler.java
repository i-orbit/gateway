package com.inmaytide.orbit.gateway.handler;

import com.inmaytide.orbit.commons.consts.CacheNames;
import com.inmaytide.orbit.commons.utils.ValueCaches;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * @author inmaytide
 * @since 2023/5/12
 */
public class LogoutHandler extends AbstractHandler {

    private static final Logger LOG = LoggerFactory.getLogger(LogoutHandler.class);

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    public Mono<ServerResponse> logout(ServerRequest request) {
        String token = getAccessToken(request);
        if (StringUtils.isNotBlank(token)) {
            authorizationService.revokeToken(token);
            ValueCaches.delete(CacheNames.REFRESH_TOKEN_STORE, token);
        }
        return ServerResponse.noContent().build();
    }
}
