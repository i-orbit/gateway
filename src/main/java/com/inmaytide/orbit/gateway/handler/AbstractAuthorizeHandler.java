package com.inmaytide.orbit.gateway.handler;

import com.inmaytide.exception.translator.ThrowableTranslator;
import com.inmaytide.exception.web.AccessDeniedException;
import com.inmaytide.exception.web.HttpResponseException;
import com.inmaytide.exception.web.domain.DefaultResponse;
import com.inmaytide.orbit.commons.consts.ParameterNames;
import com.inmaytide.orbit.commons.consts.Platforms;
import com.inmaytide.orbit.commons.domain.Oauth2Token;
import com.inmaytide.orbit.commons.domain.OrbitClientDetails;
import com.inmaytide.orbit.commons.log.OperateResult;
import com.inmaytide.orbit.commons.log.OperationLogMessageProducer;
import com.inmaytide.orbit.commons.log.domain.OperationLog;
import com.inmaytide.orbit.commons.service.uaa.AuthorizationService;
import com.inmaytide.orbit.commons.utils.HttpUtils;
import com.inmaytide.orbit.gateway.configuration.ApplicationProperties;
import com.inmaytide.orbit.gateway.configuration.ErrorCode;
import com.inmaytide.orbit.gateway.domain.Credentials;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseCookie;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.net.InetSocketAddress;

/**
 * @author inmaytide
 * @since 2023/5/12
 */
public abstract class AbstractAuthorizeHandler extends AbstractHandler {

    protected final AuthorizationService authorizationService;

    protected final OperationLogMessageProducer producer;

    protected final ApplicationProperties properties;

    protected final ThrowableTranslator<HttpResponseException> throwableTranslator;

    protected AbstractAuthorizeHandler(AuthorizationService authorizationService, OperationLogMessageProducer producer, ApplicationProperties properties, ThrowableTranslator<HttpResponseException> throwableTranslator) {
        this.authorizationService = authorizationService;
        this.producer = producer;
        this.properties = properties;
        this.throwableTranslator = throwableTranslator;
    }


    public static String getClientIpAddress(ServerRequest request) {
        for (String name : HttpUtils.HEADER_NAMES_FOR_CLIENT_ID) {
            String value = request.headers().firstHeader(name);
            if (StringUtils.isNotBlank(value) && !StringUtils.equals("unknown", value)) {
                return HttpUtils.getIpAddress(value);
            }
        }
        return request.remoteAddress().map(InetSocketAddress::getHostName).orElse(StringUtils.EMPTY);
    }

    protected void assertAllowAccessSource(ServerRequest request, Credentials credentials) {
        String ipAddress = getClientIpAddress(request);
        String geolocation = searchIpAddressGeolocation(ipAddress);
        getLogger().debug("Client IP Address: {}", ipAddress);
        getLogger().debug("Client IP Address Geolocation: {}", geolocation);
        if (properties.getDisabledAccessSources().stream().anyMatch(e -> ipAddress.contains(e) || geolocation.contains(e))
                || properties.getEnabledAccessSources().stream().noneMatch(geolocation::contains)) {
            OperationLog log = buildOperationLog(request, credentials.getPlatform());
            log.setResult(OperateResult.FAIL);
            log.setArguments(credentials.toString());
            log.setResponse("受限地区访问");
            producer.produce(log);
            throw new AccessDeniedException(ErrorCode.E_0x00200002);
        }
    }

    protected Integer getFailuresNumber(String username) {

    }

    protected void accumulateFailuresNumber(String username) {

    }

    protected Oauth2Token login(ServerRequest request, Credentials credentials) {
        getLogger().debug("Received login request from user \"{}\"", credentials.getUsername());
        assertAllowAccessSource(request, credentials);
        try {
            int failuresCount = getFailuresNumber(credentials.getUsername());
            if (failuresCount >= 5) {
                throw new AccessDeniedException(ErrorCode.E_0x00200003, "5", "5");
            }
            if (failuresCount >= 1) {
                if (!captchaHandler.validate(credentials.getCaptchaKey(), credentials.getCaptchaValue())) {
                    throw new AccessDeniedException(ErrorCode.E_0x000300008.getValue());
                }
            }

            Oauth2Token token = authorizationService.getToken(
                    OrbitClientDetails.getInstance().getClientId(),
                    OrbitClientDetails.getInstance().getClientSecret(),
                    credentials.getUsername(),
                    credentials.getPassword(),
                    credentials.getPlatform()
            );
            onSuccess(request, credentials);

            ResponseCookie cookie = ResponseCookie.from(ParameterNames.ACCESS_TOKEN)
                    .httpOnly(true)
                    .maxAge(token.getExpiresIn())
                    .value(token.getAccessToken())
                    .build();
            request.exchange().getResponse().addCookie(cookie);
            return token;
        } catch (Exception e) {
            accumulateFailuresNumber(credentials.getUsername());
            onFailed(request, credentials, e);
            throw e;
        }
    }

    private void onSuccess(ServerRequest request, Credentials credentials) {
        User user = authorizationService.findUserByUsername(credentials.getUsername());
        OperationLog log = buildOperationLog(request, credentials.getPlatform());
        log.setOperator(user.getId());
        log.setResult(OperateResult.SUCCESS);
        log.setArguments(credentials.toString());
        producer.produce(log);
    }

    private void onFailed(ServerRequest request, Credentials credentials, Throwable e) {
        OperationLog log = buildOperationLog(request, credentials.getPlatform());
        log.setResult(OperateResult.FAIL);
        log.setArguments(credentials.toString());
        throwableTranslator
                .translate(e)
                .ifPresent(ex -> log.setResponse(DefaultResponse.withException(ex).URL(request.path()).build().toString()));
        producer.produce(log);
    }

    private OperationLog buildOperationLog(ServerRequest request, Platforms platform) {
    }

}
