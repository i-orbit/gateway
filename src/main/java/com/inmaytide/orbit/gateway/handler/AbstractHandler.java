package com.inmaytide.orbit.gateway.handler;

import com.inmaytide.orbit.commons.consts.HttpHeaderNames;
import com.inmaytide.orbit.commons.consts.Marks;
import com.inmaytide.orbit.commons.consts.ParameterNames;
import com.inmaytide.orbit.commons.domain.Oauth2Token;
import com.inmaytide.orbit.commons.service.uaa.AuthorizationService;
import com.inmaytide.orbit.commons.utils.HttpUtils;
import org.apache.commons.lang3.StringUtils;
import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * @author inmaytide
 * @since 2020/12/12
 */
public abstract class AbstractHandler {

    @Lazy
    @Autowired
    protected AuthorizationService authorizationService;

    @Autowired
    private Searcher searcher;

    private ResponseCookie buildAccessTokenCookie(Oauth2Token token) {
        return ResponseCookie.from(ParameterNames.ACCESS_TOKEN)
                .httpOnly(true)
                .path("/")
                .maxAge(token.getExpiresIn())
                .value(token.getAccessToken())
                .build();
    }

    protected void setAccessTokenCookie(ServerRequest request, Oauth2Token token) {
        request.exchange().getResponse().addCookie(buildAccessTokenCookie(token));
    }

    protected void setAccessTokenCookie(ServerWebExchange exchange, Oauth2Token token) {
        exchange.getResponse().addCookie(buildAccessTokenCookie(token));
    }

    protected String getClientIpAddress(ServerRequest request) {
        for (String name : HttpUtils.HEADER_NAMES_FOR_CLIENT_ID) {
            String value = request.headers().firstHeader(name);
            if (StringUtils.isNotBlank(value) && !StringUtils.equalsIgnoreCase("unknown", value)) {
                return HttpUtils.getIpAddress(value);
            }
        }
        return request.remoteAddress().map(InetSocketAddress::getHostName).orElse(StringUtils.EMPTY);
    }

    protected String getClientIpAddress(ServerHttpRequest request) {
        for (String name : HttpUtils.HEADER_NAMES_FOR_CLIENT_ID) {
            String value = request.getHeaders().getFirst(name);
            if (StringUtils.isNotBlank(value) && !StringUtils.equalsIgnoreCase("unknown", value)) {
                return HttpUtils.getIpAddress(value);
            }
        }
        return request.getRemoteAddress() == null ? StringUtils.EMPTY : request.getRemoteAddress().getHostName();
    }

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
        token = token.replace(HttpHeaderNames.AUTHORIZATION_PREFIX, "");
        getLogger().debug("Received access token from the request is \"{}\"", token);
        return token;
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
        token = token.replace(HttpHeaderNames.AUTHORIZATION_PREFIX, "");
        getLogger().debug("Received access token from the request is \"{}\"", token);
        return token;
    }

    protected String searchIpAddressGeolocation(@Nullable String ipAddress) {
        if (StringUtils.isNotBlank(ipAddress)) {
            if (StringUtils.equalsIgnoreCase(ipAddress, "localhost") || "127.0.0.1".equals(ipAddress)) {
                return Marks.LOCAL.getValue();
            }
            try {
                String region = searcher.search(ipAddress);
                if (StringUtils.isBlank(region)) {
                    return Marks.NOT_APPLICABLE.getValue();
                }
                String[] regions = region.split("\\|");
                String country = regions[0];
                for (int i = regions.length - 2; i >= 0; i--) {
                    if ("内网IP".equalsIgnoreCase(regions[i])) {
                        return Marks.LAN.getValue();
                    }
                    if (!"0".equals(regions[i])) {
                        return i != 0 ? country + "-" + regions[i] : regions[i];
                    }
                }
            } catch (Exception e) {
                if (getLogger().isDebugEnabled()) {
                    getLogger().error("Failed to search geolocation of ip address \"{}\" with ip2region Searcher, Cause by: \n", ipAddress, e);
                } else {
                    getLogger().error("Failed to search geolocation of ip address \"{}\" with ip2region Searcher", ipAddress);
                }
            }
        }
        return Marks.NOT_APPLICABLE.getValue();
    }

    protected abstract Logger getLogger();

}
