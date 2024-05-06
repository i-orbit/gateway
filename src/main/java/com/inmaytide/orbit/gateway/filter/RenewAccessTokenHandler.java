package com.inmaytide.orbit.gateway.filter;

import com.inmaytide.exception.web.BadCredentialsException;
import com.inmaytide.exception.web.UnauthorizedException;
import com.inmaytide.orbit.commons.constants.Constants;
import com.inmaytide.orbit.commons.domain.Oauth2Token;
import com.inmaytide.orbit.commons.utils.ValueCaches;
import com.inmaytide.orbit.gateway.configuration.ErrorCode;
import com.inmaytide.orbit.gateway.handler.AbstractHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 当本次请求中的 <code>access_token</code> 有效时间小于 {@value REQUIRED_REFRESH_TOKEN_LT} 秒时 <br/>
 * 自动调用刷新 token 的接口获取新的 access_token 写入本次请求的 Response 和 Request 中
 *
 * @author inmaytide
 * @since 2020/12/12
 */
@Component
public class RenewAccessTokenHandler extends AbstractHandler implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RenewAccessTokenHandler.class);

    /**
     * access_token 离过期时间还有多少秒时自动刷新
     */
    private static final long REQUIRED_REFRESH_TOKEN_LT = 30;

    /**
     * {@link Oauth2Token} 在临时存储容器中保留时间
     */
    private static final long TOKEN_TEMPORARY_STORE_MILLISECONDS = 60 * 1000;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        wasForciblyCancelled(exchange);
        String accessToken = getAccessToken(exchange);
        if (StringUtils.isNotBlank(accessToken) && requireRenew(accessToken)) {
            log.debug("The access token needs to be refreshed");
            synchronized (accessToken.intern()) {
                exchange = renewToken(exchange, accessToken);
            }
        }
        return chain.filter(exchange);
    }

    private void wasForciblyCancelled(ServerWebExchange exchange) {
        getRefreshToken(exchange)
                .flatMap(refreshToken -> ValueCaches.get(Constants.CacheNames.DISABLED_REFRESH_TOKEN, refreshToken))
                .ifPresent(value -> {
                    log.debug("The access token was forcibly cancelled by other users");
                    throw new UnauthorizedException(ErrorCode.E_0x00200001);
                });
    }

    private Oauth2Token refreshToken(@Nullable String refreshToken) {
        if (StringUtils.isBlank(refreshToken)) {
            throw new BadCredentialsException();
        }
        return authorizationService.refreshToken(refreshToken);
    }

    private void doCache(final String key, final Oauth2Token value) {
        // 将新token与旧token放入缓存绑定
        ValueCaches.put(Constants.CacheNames.TOKEN_TEMPORARY_STORE, key, value.getAccessToken(), TOKEN_TEMPORARY_STORE_MILLISECONDS, TimeUnit.MILLISECONDS);
    }

    private ServerWebExchange renewToken(ServerWebExchange exchange, String accessToken) {
        try {
            String refreshedToken = ValueCaches.get(Constants.CacheNames.TOKEN_TEMPORARY_STORE, accessToken).orElse(null);
            if (refreshedToken != null) {
                log.debug("The access token is refreshed within {}ms and a new access token is read from the cache", TOKEN_TEMPORARY_STORE_MILLISECONDS);
            } else {
                log.debug("No new access token is read from the cache, request the remote api to refresh the access token");
                Optional<String> refreshToken = getRefreshToken(exchange);
                if (refreshToken.isEmpty()) {
                    return exchange;
                }
                log.debug("Refresh Token value is \"{}\"", refreshToken.get());
                Oauth2Token token = refreshToken(refreshToken.get());
                log.debug("The refresh result is {}", token);
                refreshedToken = token.getAccessToken();
                doCache(accessToken, token);
                setTokenCookies(exchange, token);
            }
            ServerHttpRequest request = exchange.getRequest().mutate().header(Constants.HttpHeaderNames.AUTHORIZATION, Constants.HttpHeaderNames.AUTHORIZATION_PREFIX + refreshedToken).build();
            return exchange.mutate().request(request).build();
        } catch (Exception e) {
            log.error("Failed to refresh token, Cause by: ", e);
            if (e instanceof BadCredentialsException) {
                throw e;
            }
            return exchange;
        }
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }

    private Optional<String> getRefreshToken(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(Constants.RequestParameters.REFRESH_TOKEN);
        return cookie != null ? Optional.of(cookie.getValue()) : Optional.empty();
    }

    private boolean requireRenew(String accessToken) {
        Long expire = ValueCaches.getExpire(Constants.CacheNames.ACCESS_TOKEN_STORE, accessToken);
        log.debug("Access token \"{}\" will expired in {} seconds", accessToken, expire);
        expire = expire == null ? 0 : expire;
        return expire < REQUIRED_REFRESH_TOKEN_LT;
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
}
