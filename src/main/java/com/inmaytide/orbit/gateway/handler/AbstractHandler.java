package com.inmaytide.orbit.gateway.handler;

import com.inmaytide.orbit.commons.consts.HttpHeaderNames;
import com.inmaytide.orbit.commons.consts.Marks;
import com.inmaytide.orbit.commons.consts.ParameterNames;
import com.inmaytide.orbit.commons.service.uaa.AuthorizationService;
import org.apache.commons.lang3.StringUtils;
import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * @author inmaytide
 * @since 2020/12/12
 */
public abstract class AbstractHandler {

    @Autowired
    private Searcher searcher;

    protected String getAccessToken(ServerRequest request) {
        String token = request.headers().firstHeader(HttpHeaderNames.AUTHORIZATION);
        if (StringUtils.isBlank(token)) {
            HttpCookie cookie = request.cookies().getFirst(ParameterNames.ACCESS_TOKEN);
            if (cookie != null) {
                token = cookie.getValue();
            }
        }
        if (StringUtils.isBlank(token)) {
            token = request.queryParam(ParameterNames.ACCESS_TOKEN).orElse(StringUtils.EMPTY);
        }
        if (StringUtils.isBlank(token)) {
            getLogger().debug("There is no access token in this request");
            return StringUtils.EMPTY;
        }
        return token.replace(HttpHeaderNames.AUTHORIZATION_PREFIX, "");
    }

    protected String getAccessToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(HttpHeaderNames.AUTHORIZATION);
        if (StringUtils.isBlank(token)) {
            HttpCookie cookie = request.getCookies().getFirst(ParameterNames.ACCESS_TOKEN);
            if (cookie != null) {
                token = cookie.getValue();
            }
        }
        if (StringUtils.isBlank(token)) {
            token = request.getQueryParams().getFirst(ParameterNames.ACCESS_TOKEN);
        }
        if (StringUtils.isBlank(token)) {
            getLogger().debug("There is no access token in this request");
            return StringUtils.EMPTY;
        }
        return token.replace(HttpHeaderNames.AUTHORIZATION_PREFIX, "");
    }

    protected String searchIpAddressGeolocation(@Nullable String ipAddress) {
        if (StringUtils.isNotBlank(ipAddress)) {
            try {
                String region = searcher.search(ipAddress);
                if (StringUtils.isBlank(region)) {
                    return Marks.NOT_APPLICABLE.getValue();
                }
                String[] regions = region.split("\\|");
                String country = regions[0];
                for (int i = regions.length - 2; i >= 0; i--) {
                    if ("内网IP".equalsIgnoreCase(regions[i])) {
                        return "LAN";
                    }
                    if (!"0".equals(regions[i])) {
                        return i != 0 ? country + "-" + regions[i] : regions[i];
                    }
                }
            } catch (Exception e) {
                getLogger().error("Failed to search geolocation of ip address \"{}\" with ip2region Searcher, Cause by: \n", ipAddress, e);
            }
        }
        return Marks.NOT_APPLICABLE.getValue();
    }

    protected abstract Logger getLogger();

}
