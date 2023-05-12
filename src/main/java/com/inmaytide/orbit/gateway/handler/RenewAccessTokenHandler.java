package com.inmaytide.orbit.gateway.handler;

import com.inmaytide.exception.web.BadCredentialsException;
import com.inmaytide.exception.web.UnauthorizedException;
import com.inmaytide.orbit.commons.consts.Marks;
import com.inmaytide.orbit.commons.domain.Oauth2Token;
import com.inmaytide.orbit.commons.domain.OrbitClientDetails;
import com.inmaytide.orbit.gateway.configuration.ErrorCode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * 当本次请求中的 <code>access_token</code> 过期时间小于 {@value REQUIRED_REFRESH_TOKEN_LT} 秒时自动获取新的 access_token 写入本次请求的 Response 和 Request 中
 *
 * @author luomiao
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
     * 刷新 access_token 请求接口使用的 grant_type
     */
    private static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    /**
     * {@link Oauth2Token} 在临时存储容器中保留时间
     */
    private static final long TOKEN_TEMPORARY_STORE_MILLISECONDS = 60 * 1000;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String accessToken = getAccessToken(exchange.getRequest());
        if (StringUtils.isNotBlank(accessToken)) {
            log.debug("Access token of this request is \"{}\"", accessToken);
        }
        wasForciblyCancelled(accessToken);
        if (StringUtils.isNotBlank(accessToken) && requireRenew(accessToken)) {
            synchronized (accessToken.intern()) {
                log.debug("Access token is required to refresh");
                exchange = renewToken(exchange, accessToken);
            }
        }
        return chain.filter(exchange);
    }

    private void wasForciblyCancelled(String accessToken) {
        if (Marks.USER_FORCE_LOGOUT.getValue().equals(getRefreshToken(accessToken))) {
            log.debug("Token was forcibly cancelled by other users");
            throw new UnauthorizedException(ErrorCode.E_0x00200001);
        }
    }

    private Oauth2Token refreshToken(@Nullable String refreshToken) {
        if (StringUtils.isBlank(refreshToken)) {
            throw new BadCredentialsException(ErrorCode.E_0x000300002.getValue());
        }
        return authorizationService.refreshToken(
                OrbitClientDetails.getInstance().getClientId(),
                OrbitClientDetails.getInstance().getClientSecret(),
                refreshToken
        );
    }

    private void doCache(final String key, final Oauth2Token value) {
        // 将新token与旧token放入缓存绑定
        ValueCaches.put(Constants.CacheNames.TOKEN_TEMPORARY_STORE, key, value.getAccessToken());

        // 更新token与用户名关联缓存
        ValueCaches.get(Constants.CacheNames.TOKEN_USERNAME_ASSOCIATION, key).ifPresent(username -> {
            ValueCaches.put(Constants.CacheNames.TOKEN_USERNAME_ASSOCIATION, value.getAccessToken(), username);
        });

        // 更新token与登录平台关联缓存
        ValueCaches.get(Constants.CacheNames.TOKEN_PLATFORM, key).ifPresent(platform -> {
            ValueCaches.put(Constants.CacheNames.TOKEN_PLATFORM, value.getAccessToken(), platform);
        });

        // 更新token与refresh token关联缓存
        ValueCaches.put(Constants.CacheNames.REFRESH_TOKEN, value.getAccessToken(), value.getRefreshToken());

        // 清除无用缓存
        ValueCaches.expire(Constants.CacheNames.REFRESH_TOKEN, key, TOKEN_TEMPORARY_STORE_MILLISECONDS, TimeUnit.MILLISECONDS);
        ValueCaches.expire(Constants.CacheNames.TOKEN_TEMPORARY_STORE, key, TOKEN_TEMPORARY_STORE_MILLISECONDS, TimeUnit.MILLISECONDS);
        ValueCaches.expire(Constants.CacheNames.TOKEN_USERNAME_ASSOCIATION, key, TOKEN_TEMPORARY_STORE_MILLISECONDS, TimeUnit.MILLISECONDS);
        ValueCaches.expire(Constants.CacheNames.TOKEN_PLATFORM, key, TOKEN_TEMPORARY_STORE_MILLISECONDS, TimeUnit.MILLISECONDS);
    }

    private ServerWebExchange renewToken(ServerWebExchange exchange, String accessToken) {
        try {
            String refreshedToken = ValueCaches.get(Constants.CacheNames.TOKEN_TEMPORARY_STORE, accessToken).orElse(null);
            if (refreshedToken != null) {
                log.debug("The access_token is refreshed within {} milliseconds, and the new access_token is read from the TOKEN_TEMPORARY_CACHE", TOKEN_TEMPORARY_STORE_MILLISECONDS);
            } else {
                log.debug("No new access_token is retrieved from the TOKEN_TEMPORARY_CACHE, request the remote api to refresh the access_token");
                String refreshToken = getRefreshToken(accessToken);
                log.debug("Refresh Token value is \"{}\"", refreshToken);
                Oauth2Token token = refreshToken(refreshToken);
                log.debug("The refresh result is {}", token);
                refreshedToken = token.getAccessToken();
                doCache(accessToken, token);
            }
            exchange.getResponse().getHeaders().set(Constants.HeaderNames.AUTHORIZATION, refreshedToken);
            ServerHttpRequest request = exchange.getRequest().mutate().header(Constants.HeaderNames.AUTHORIZATION, Constants.HeaderNames.AUTHORIZATION_PREFIX + refreshedToken).build();
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
        return -10;
    }

    @Nullable
    private String getRefreshToken(String accessToken) {
        return ValueCaches.get(Constants.CacheNames.REFRESH_TOKEN, accessToken).orElse(null);
    }

    private boolean requireRenew(String accessToken) {
        Long expire = stringRedisTemplate.getExpire(TIMER_ACCESS_TOKEN_EXPIRES + "::" + accessToken);
        log.debug("Access token \"{}\" will expired in {} seconds", accessToken, expire);
        expire = expire == null ? 0 : expire;
        return expire < REQUIRED_REFRESH_TOKEN_LT;
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
}
