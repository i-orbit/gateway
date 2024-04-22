package com.inmaytide.orbit.gateway.filter;

import com.inmaytide.orbit.commons.constants.Constants;
import com.inmaytide.orbit.gateway.handler.AbstractHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;

/**
 * Since Spring Security OAuth2 by default only recognizes access tokens named "Authorization" in the HTTP header,
 * in order to support passing the access token through cookies and request parameters, it is necessary to
 * transfer the value of "access_token" from the request parameters or cookies to the HTTP header named "Authorization".
 * <br/><br/>
 *
 * <b>2022/5/18:</b> After removing the default configuration, it has been verified that Spring Security OAuth2 does not automatically
 * fetch the AccessToken from the request parameters or cookies. Further enhancements will be explored to optimize
 * this functionality once a better solution is discovered.
 *
 * @deprecated <b>2024/04/22: </b> Transfer to overriding "OAuth2TokenIntrospectionAuthenticationConverter" in uaa module
 * @author luomiao
 * @since 2021/4/25
 */
//@Component
@Deprecated
public class AccessTokenHandler extends AbstractHandler implements GlobalFilter, Ordered {

    private final static Logger LOG = LoggerFactory.getLogger(AccessTokenHandler.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = exchange.getRequest().getHeaders().getFirst(Constants.HttpHeaderNames.AUTHORIZATION);
        if (StringUtils.isBlank(token)) {
            token = getAccessToken(exchange.getRequest());
            if (StringUtils.isNotBlank(token)) {
                URI uri = exchange.getRequest().getURI();
                uri = UriComponentsBuilder.fromUri(uri).replaceQueryParam(Constants.RequestParameters.ACCESS_TOKEN, Collections.emptyList()).build().toUri();
                ServerHttpRequest request = exchange.getRequest()
                        .mutate()
                        .header(Constants.HttpHeaderNames.AUTHORIZATION, Constants.HttpHeaderNames.AUTHORIZATION_PREFIX + token)
                        .uri(uri).build();
                exchange = exchange.mutate().request(request).build();
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1000;
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
