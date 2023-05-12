package com.inmaytide.orbit.gateway.handler;

import com.carrot.commons.business.dto.User;
import com.carrot.commons.business.dto.UserOnline;
import com.carrot.commons.consts.Constants;
import com.carrot.commons.consts.Platform;
import com.carrot.commons.utils.RequestUtils;
import com.carrot.commons.utils.ValueCaches;
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
 * 刷新在线用户的最后活动时间
 *
 * @author inmaytide
 * @since 2022/3/20
 */
@Component
public class UpdateActivityTimeHandler extends AbstractHandler implements GlobalFilter, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateActivityTimeHandler.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = getAccessToken(exchange.getRequest());
        if (StringUtils.isNotBlank(token)) {
            String platform = ValueCaches.get(Constants.CacheNames.TOKEN_PLATFORM, token).orElse(Platform.WEB.name());
            ValueCaches.get(Constants.CacheNames.TOKEN_USERNAME_ASSOCIATION, token)
                    .ifPresent(username -> setOnlineCache(exchange, username, token, Platform.valueOf(platform)));
        }
        return chain.filter(exchange);
    }

    private String getOnlineUserCacheKey(String username, Platform platform) {
        return platform.name() + "::" + username;
    }

    private void setOnlineCache(ServerWebExchange exchange, String username, String token, Platform platform) {
        UserOnline userOnline = ValueCaches.get(Constants.CacheNames.ONLINE_USERS, getOnlineUserCacheKey(username, platform)).map(UserOnline::formJson).orElse(new UserOnline());
        if (userOnline.getUserId() == null) {
            getLogger().debug("Set user online cache for {}, {}, {}", username, token, platform.name());
            User user = userService.findUserByUsername(username);
            userOnline.setUserId(user.getId());
            userOnline.setOnlineTime(Instant.now());
            userOnline.setPlatform(platform);
            String ipAddress = RequestUtils.getIpAddress(exchange.getRequest());
            userOnline.setIpAddress(ipAddress + "(" + getIpRegion(ipAddress) + ")");
        }
        userOnline.setLastActivityTime(userOnline.getOnlineTime());
        ValueCaches.put(Constants.CacheNames.ONLINE_USERS, getOnlineUserCacheKey(username, platform), userOnline.toString());
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
