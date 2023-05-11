package com.inmaytide.orbit.gateway.handler;

import com.inmaytide.orbit.commons.service.uaa.AuthorizationService;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;

/**
 * @author inmaytide
 * @since 2020/12/12
 */
public abstract class AbstractAuthorizeHandler {

    @Autowired
    protected AuthorizationService authorizationService;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @Autowired
    private Searcher searcher;

    protected String getAccessToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(Constants.HeaderNames.AUTHORIZATION);
        if (StringUtils.isBlank(token)) {
            token = request.getQueryParams().getFirst("access_token");
        }
        if (StringUtils.isBlank(token)) {
            getLogger().debug("There is no access token in this request");
            return StringUtils.EMPTY;
        }
        return token.replace(Constants.HeaderNames.AUTHORIZATION_PREFIX, "");
    }

    protected String getIpRegion(@Nullable String ipAddress) {
        if (StringUtils.isNotBlank(ipAddress)) {
            try {
                String region = searcher.search(ipAddress);
                if (StringUtils.isBlank(region)) {
                    return Constants.Marks.NOT_APPLICABLE;
                }
                String[] regions = region.split("\\|");
                String country = regions[0];
                for (int i = regions.length - 2; i >= 0; i--) {
                    if ("内网IP".equalsIgnoreCase(regions[i])) {
                        return "LAN";
                    }
                    if (!"0".equals(regions[i])) {
                        return i != 0?country+"-"+regions[i]:regions[i];
                    }
                }
                return Constants.Marks.NOT_APPLICABLE;
            } catch (Exception e) {
                getLogger().error("Failed to get ip region with ip2region Searcher, Cause by: \n", e);
            }
        }
        return Constants.Marks.NOT_APPLICABLE;
    }

    protected abstract Logger getLogger();

}
