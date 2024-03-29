package com.inmaytide.orbit.gateway.handler;

import com.inmaytide.exception.translator.ThrowableTranslator;
import com.inmaytide.exception.web.AccessDeniedException;
import com.inmaytide.exception.web.BadCredentialsException;
import com.inmaytide.exception.web.HttpResponseException;
import com.inmaytide.exception.web.ServiceUnavailableException;
import com.inmaytide.exception.web.domain.DefaultResponse;
import com.inmaytide.orbit.commons.consts.HttpHeaderNames;
import com.inmaytide.orbit.commons.consts.Is;
import com.inmaytide.orbit.commons.domain.Oauth2Token;
import com.inmaytide.orbit.commons.log.OperationLogMessageProducer;
import com.inmaytide.orbit.commons.log.domain.OperationLog;
import com.inmaytide.orbit.commons.service.uaa.UserService;
import com.inmaytide.orbit.commons.utils.ValueCaches;
import com.inmaytide.orbit.gateway.configuration.ApplicationProperties;
import com.inmaytide.orbit.gateway.configuration.ErrorCode;
import com.inmaytide.orbit.gateway.domain.Credentials;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author inmaytide
 * @since 2023/5/12
 */
public abstract class AbstractAuthorizeHandler extends AbstractHandler {

    protected final static int MAXIMUM_NUMBER_OF_FAILED_LOGIN_ATTEMPTS = 5;

    protected final static int EXCEED_NUMBER_LOCK_TIMES_IN_MINUTE = 5;

    protected final static String CACHE_NAME_LOGIN_FAILURE_NUMBERS = "LOGIN_FAILURE_NUMBERS";

    protected final OperationLogMessageProducer producer;

    protected final ApplicationProperties properties;

    protected final ThrowableTranslator<HttpResponseException> throwableTranslator;

    protected final UserService userService;

    private final CaptchaHandler captchaHandler;

    protected AbstractAuthorizeHandler(OperationLogMessageProducer producer, ApplicationProperties properties, ThrowableTranslator<HttpResponseException> throwableTranslator, UserService userService, CaptchaHandler captchaHandler) {
        this.producer = producer;
        this.properties = properties;
        this.throwableTranslator = throwableTranslator;
        this.userService = userService;
        this.captchaHandler = captchaHandler;
    }

    /**
     * 验证访问来源的黑白名单
     */
    protected void assertAllowAccessSource(ServerRequest request, Credentials credentials) {
        String ipAddress = getClientIpAddress(request);
        String geolocation = searchIpAddressGeolocation(ipAddress);
        getLogger().debug("Client IP Address: {}", ipAddress);
        getLogger().debug("Client IP Address Geolocation: {}", geolocation);
        if ((properties.getDisabledAccessSources() != null && properties.getDisabledAccessSources().stream().anyMatch(e -> ipAddress.contains(e) || geolocation.contains(e)))
                || (properties.getEnabledAccessSources() != null && properties.getEnabledAccessSources().stream().noneMatch(geolocation::contains))) {
            OperationLog log = buildOperationLog(request, credentials);
            log.setResult(Is.N);
            log.setArguments(credentials.toString());
            log.setResponse("受限地区访问");
            producer.produce(log);
            throw new AccessDeniedException(ErrorCode.E_0x00200002);
        }
    }

    /**
     * 检查登录尝试的失败次数
     * <ol>
     *     <li>大于等于 {@linkplain AbstractAuthorizeHandler#MAXIMUM_NUMBER_OF_FAILED_LOGIN_ATTEMPTS} 时禁止登录</li>
     *     <li>大于等于 <code>1</code> 时需要强制验证验证码</li>
     * </ol>
     */
    protected void checkFailureNumbers(Credentials credentials) {
        int failuresNumber = getFailuresNumber(credentials.getUsername());
        if (failuresNumber >= MAXIMUM_NUMBER_OF_FAILED_LOGIN_ATTEMPTS) {
            throw new AccessDeniedException(ErrorCode.E_0x00200003, String.valueOf(MAXIMUM_NUMBER_OF_FAILED_LOGIN_ATTEMPTS), String.valueOf(EXCEED_NUMBER_LOCK_TIMES_IN_MINUTE));
        }
        if (failuresNumber >= 1) {
            if (!captchaHandler.validate(credentials.getCaptchaKey(), credentials.getCaptchaValue())) {
                throw new BadCredentialsException(ErrorCode.E_0x00200007);
            }
        }
    }

    protected Integer getFailuresNumber(String username) {
        return ValueCaches.get(CACHE_NAME_LOGIN_FAILURE_NUMBERS, username)
                .map(NumberUtils::createInteger)
                .orElse(0);
    }

    protected void accumulateFailuresNumber(String username) {
        Integer number = getFailuresNumber(username);
        ValueCaches.put(CACHE_NAME_LOGIN_FAILURE_NUMBERS, username, String.valueOf(number + 1), EXCEED_NUMBER_LOCK_TIMES_IN_MINUTE, TimeUnit.MINUTES);
    }

    protected Oauth2Token login(ServerRequest request, Credentials credentials) {
        credentials.validate();
        getLogger().debug("Received login request from user \"{}\"", credentials.getUsername());
        try {
            assertAllowAccessSource(request, credentials);
            checkFailureNumbers(credentials);
            Oauth2Token token = authorizationService.getToken(
                    credentials.getUsername(),
                    credentials.getPassword(),
                    credentials.getPlatform(),
                    credentials.getForcedReplacement()
            );
            onSuccess(request, credentials);
            setAccessTokenCookie(request, token);
            return token;
        } catch (Exception e) {
            onFailed(request, credentials, e);
            throwableTranslator.translate(e).ifPresent(ex -> {
                // 如果账号不存在或密码错误, 根据安全管理要求整合错误信息模糊具体的错误提醒
                if (Objects.equals(ex.getCode(), "0x00100002") || Objects.equals(ex.getCode(), "0x00100003")) {
                    throw new BadCredentialsException(ErrorCode.E_0x00200010);
                }
                // 账号已登录或认证服务不可用不累加异常次数
                if (Objects.equals(ex.getCode(), "0x00100001") || ex instanceof ServiceUnavailableException) {
                    throw e;
                }
            });
            accumulateFailuresNumber(credentials.getUsername());
            throw e;
        }
    }

    private void onSuccess(ServerRequest request, Credentials credentials) {
        ValueCaches.delete(CACHE_NAME_LOGIN_FAILURE_NUMBERS, credentials.getUsername());
        OperationLog log = buildOperationLog(request, credentials);
        log.setResult(Is.Y);
        log.setArguments(credentials.toString());
        producer.produce(log);
    }

    private void onFailed(ServerRequest request, Credentials credentials, Throwable e) {
        OperationLog log = buildOperationLog(request, credentials);
        log.setResult(Is.N);
        log.setArguments(credentials.toString());
        throwableTranslator
                .translate(e)
                .ifPresent(ex -> log.setResponse(DefaultResponse.withException(ex).URL(request.path()).build().toString()));
        producer.produce(log);
    }

    private OperationLog buildOperationLog(ServerRequest request, Credentials credentials) {
        OperationLog log = new OperationLog();
        log.setOperationTime(Instant.now());
        log.setDescription("用户名密码登录");
        log.setBusiness("用户登录");
        log.setChain(request.headers().firstHeader(HttpHeaderNames.CALL_CHAIN));
        log.setPlatform(credentials.getPlatform().name());
        log.setPath(request.path());
        log.setHttpMethod(request.method().name());
        log.setClientDescription(request.headers().firstHeader(HttpHeaderNames.USER_AGENT));
        log.setIpAddress(getClientIpAddress(request));
        userService.getUserByUsername(credentials.getUsername()).ifPresent(user -> {
            log.setOperator(user.getId());
            log.setTenantId(user.getTenantId());
        });
        return log;
    }

}
