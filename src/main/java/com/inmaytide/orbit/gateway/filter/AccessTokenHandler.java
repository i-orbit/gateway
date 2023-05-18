package com.inmaytide.orbit.gateway.filter;

import com.inmaytide.orbit.commons.consts.HttpHeaderNames;
import com.inmaytide.orbit.commons.service.uaa.AuthorizationService;
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
 * 整理请求参数中/Cookie中的 Access Token 值, 转入 http header 中
 *
 * @author luomiao
 * @since 2021/4/25
 */
@Component
public class AccessTokenHandler extends AbstractHandler implements GlobalFilter, Ordered {

    private final static Logger LOG = LoggerFactory.getLogger(AccessTokenHandler.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = exchange.getRequest().getHeaders().getFirst(HttpHeaderNames.AUTHORIZATION);
        if (StringUtils.isBlank(token)) {
            token = getAccessToken(exchange.getRequest());
            if (StringUtils.isNotBlank(token)) {
                URI uri = exchange.getRequest().getURI();
                uri = UriComponentsBuilder.fromUri(uri).replaceQueryParam("access_token", Collections.emptyList()).build().toUri();
                ServerHttpRequest request = exchange.getRequest()
                        .mutate()
                        .header(HttpHeaderNames.AUTHORIZATION, HttpHeaderNames.AUTHORIZATION_PREFIX + token)
                        .uri(uri).build();
                exchange = exchange.mutate().request(request).build();
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -15;
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
