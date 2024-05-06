package com.inmaytide.orbit.gateway.handler;

import com.inmaytide.orbit.commons.constants.Constants;
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
        return ResponseCookie.from(Constants.RequestParameters.ACCESS_TOKEN)
                .httpOnly(true)
                .path("/")
                .maxAge(token.getExpiresIn())
                .value(token.getAccessToken())
                .build();
    }

    private ResponseCookie buildRefreshTokenCookie(Oauth2Token token) {
        return ResponseCookie.from(Constants.RequestParameters.REFRESH_TOKEN)
                .httpOnly(true)
                .path("/")
                .maxAge(token.getExpiresIn())
                .value(token.getRefreshToken())
                .build();
    }

    protected void setTokenCookies(ServerWebExchange exchange, Oauth2Token token) {
        exchange.getResponse().addCookie(buildAccessTokenCookie(token));
        exchange.getResponse().addCookie(buildRefreshTokenCookie(token));
    }

    protected String getClientIpAddress(ServerWebExchange exchange) {
        for (String name : HttpUtils.HEADER_NAMES_FOR_CLIENT_ID) {
            String value = exchange.getRequest().getHeaders().getFirst(name);
            if (StringUtils.isNotBlank(value) && !StringUtils.equalsIgnoreCase("unknown", value)) {
                return HttpUtils.getIpAddress(value);
            }
        }
        return exchange.getRequest().getRemoteAddress() == null ? StringUtils.EMPTY : exchange.getRequest().getRemoteAddress().getHostName();
    }

    protected String getAccessToken(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst(Constants.HttpHeaderNames.AUTHORIZATION);
        if (StringUtils.isBlank(token)) {
            HttpCookie cookie = exchange.getRequest().getCookies().getFirst(Constants.RequestParameters.ACCESS_TOKEN);
            if (cookie != null) {
                token = cookie.getValue();
            }
        }
        if (StringUtils.isBlank(token)) {
            token = exchange.getRequest().getQueryParams().getFirst(Constants.RequestParameters.ACCESS_TOKEN);
        }
        if (StringUtils.isBlank(token)) {
            getLogger().debug("There is no access token in this request");
            return StringUtils.EMPTY;
        }
        token = token.replace(Constants.HttpHeaderNames.AUTHORIZATION_PREFIX, "");
        getLogger().debug("Received access token from the request is \"{}\"", token);
        return token;
    }


    protected String searchIpAddressGeolocation(@Nullable String ipAddress) {
        if (StringUtils.isNotBlank(ipAddress)) {
            if (StringUtils.equalsIgnoreCase(ipAddress, "localhost") || "127.0.0.1".equals(ipAddress)) {
                return Constants.Markers.LOCAL;
            }
            try {
                String region = searcher.search(ipAddress);
                if (StringUtils.isBlank(region)) {
                    return Constants.Markers.NOT_APPLICABLE;
                }
                String[] regions = region.split("\\|");
                String country = regions[0];
                for (int i = regions.length - 2; i >= 0; i--) {
                    if ("内网IP".equalsIgnoreCase(regions[i])) {
                        return Constants.Markers.LAN;
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
        return Constants.Markers.NOT_APPLICABLE;
    }

    protected abstract Logger getLogger();

}
