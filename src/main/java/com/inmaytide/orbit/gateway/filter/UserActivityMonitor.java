package com.inmaytide.orbit.gateway.filter;

import com.inmaytide.orbit.commons.consts.CacheNames;
import com.inmaytide.orbit.commons.consts.HttpHeaderNames;
import com.inmaytide.orbit.commons.consts.Platforms;
import com.inmaytide.orbit.commons.domain.GlobalUser;
import com.inmaytide.orbit.commons.domain.OnlineUser;
import com.inmaytide.orbit.commons.service.uaa.AuthorizationService;
import com.inmaytide.orbit.commons.utils.CodecUtils;
import com.inmaytide.orbit.commons.utils.ValueCaches;
import com.inmaytide.orbit.gateway.handler.AbstractHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * 用户活动监测
 * <ol>
 *     <li>更新缓存中用户最后访问时间</li>
 *     <li>生成接口调用链标识</li>
 * </ol>
 *
 * @author inmaytide
 * @since 2022/3/20
 */
@Component
public class UserActivityMonitor extends AbstractHandler implements GlobalFilter, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(UserActivityMonitor.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 写入接口调用链表示
        exchange.getRequest().getHeaders().add(HttpHeaderNames.CALL_CHAIN, CodecUtils.generateUUID());
        // 获取登录token, 如果拿到token后, 尝试生成用户在线记录写入缓存
        String token = getAccessToken(exchange.getRequest());
        if (StringUtils.isNotBlank(token)) {
            new Thread(() -> setOnlineCache(exchange, token)).start();
        }
        return chain.filter(exchange);
    }

    private String getOnlineUserCacheKey(Long userId, Platforms platform) {
        return userId + "_" + platform.name();
    }

    private void setOnlineCache(ServerWebExchange exchange, String accessToken) {
        Platforms platform = authorizationService.getCurrentPlatform(accessToken);
        GlobalUser user = authorizationService.getCurrentUser(accessToken);
        if (platform == null || user == null) {
            LOG.warn("Current access token is not associated with any user information or login platform");
            return;
        }
        try {
            OnlineUser onlineUser = ValueCaches.get(CacheNames.ONLINE_USER, getOnlineUserCacheKey(user.getId(), platform)).map(OnlineUser::formJson).orElse(new OnlineUser());
            if (onlineUser.getId() == null) {
                onlineUser.setId(user.getId());
                onlineUser.setOnlineTime(Instant.now());
                onlineUser.setPlatform(platform);
                String ipAddress = getClientIpAddress(exchange.getRequest());
                onlineUser.setIpAddress(ipAddress + "(" + searchIpAddressGeolocation(ipAddress) + ")");
            }
            onlineUser.setLastActivityTime(Instant.now());
            ValueCaches.put(CacheNames.ONLINE_USER, getOnlineUserCacheKey(user.getId(), platform), onlineUser.toString());
        } catch (Exception e) {
            LOG.error("Failed to write online user[{}, {}] information to cache, Cause by: ", user.getId(), platform.name(), e);
        }
    }

    @Override
    public int getOrder() {
        return -10;
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
