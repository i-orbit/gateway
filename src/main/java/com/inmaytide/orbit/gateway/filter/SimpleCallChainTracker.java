package com.inmaytide.orbit.gateway.filter;

import com.inmaytide.orbit.commons.constants.Constants;
import com.inmaytide.orbit.commons.utils.CodecUtils;
import com.inmaytide.orbit.gateway.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Simple call chain tracker
 * <ol>
 *     <li>Generate API call chain identifier into http header for current request</li>
 * </ol>
 *
 * @author inmaytide
 * @since 2022/3/20
 */
@Component
public class SimpleCallChainTracker extends AbstractHandler implements GlobalFilter, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleCallChainTracker.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 写入接口调用链标识
        ServerHttpRequest request = exchange.getRequest().mutate().header(Constants.HttpHeaderNames.CALL_CHAIN, CodecUtils.randomUUID()).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
