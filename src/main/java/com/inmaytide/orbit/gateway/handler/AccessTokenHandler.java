package com.inmaytide.orbit.gateway.handler;

import com.carrot.commons.consts.Constants;
import org.apache.commons.lang3.StringUtils;
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
 * @author luomiao
 * @since 2021/4/25
 */
@Component
public class AccessTokenHandler implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = exchange.getRequest().getHeaders().getFirst(Constants.HeaderNames.AUTHORIZATION);
        if (StringUtils.isBlank(token)) {
            token = exchange.getRequest().getQueryParams().getFirst("access_token");
            if (StringUtils.isNotBlank(token)) {
                URI uri = exchange.getRequest().getURI();
                uri = UriComponentsBuilder.fromUri(uri).replaceQueryParam("access_token", Collections.emptyList()).build().toUri();
                ServerHttpRequest request = exchange.getRequest().mutate()
                        .header(Constants.HeaderNames.AUTHORIZATION, Constants.HeaderNames.AUTHORIZATION_PREFIX + token)
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
}
