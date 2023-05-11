package com.inmaytide.orbit.gateway.handler;

import com.carrot.commons.business.dto.Oauth2Token;
import com.carrot.commons.business.dto.User;
import com.carrot.commons.consts.Constants;
import com.carrot.commons.consts.OperateResult;
import com.carrot.commons.consts.Platform;
import com.carrot.commons.log.LogMessageProducer;
import com.carrot.commons.log.domain.OperationLog;
import com.carrot.commons.producer.RabbitProducer;
import com.carrot.commons.security.ClientDetails;
import com.carrot.commons.utils.RequestUtils;
import com.carrot.commons.utils.ValueCaches;
import com.carrot.gateway.config.ApplicationProperties;
import com.carrot.gateway.config.ErrorCode;
import com.carrot.gateway.domain.AccessToken;
import com.carrot.gateway.domain.Credentials;
import com.carrot.gateway.domain.ScanCodeCredentials;
import com.carrot.gateway.domain.ScanCodeResult;
import com.inmaytide.exception.translator.ThrowableTranslator;
import com.inmaytide.exception.web.AccessDeniedException;
import com.inmaytide.exception.web.HttpResponseException;
import com.inmaytide.exception.web.domain.DefaultResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

/**
 * @author inmaytide
 * @since 2020/12/11
 */
@Component
public class AuthorizeHandler extends AbstractAuthorizeHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthorizeHandler.class);

    public static final String GRANT_TYPE_PASSWORD = "password";

    private static final String CACHE_KEY_FAILURES_COUNT = "FAILURES_COUNT::";

    private final LogMessageProducer producer;

    private final ThrowableTranslator<HttpResponseException> throwableTranslator;

    private final RabbitProducer rabbitProducer;

    private final CaptchaHandler captchaHandler;

    private final ApplicationProperties env;

    public AuthorizeHandler(LogMessageProducer producer, ThrowableTranslator<HttpResponseException> throwableTranslator, RabbitProducer rabbitProducer, CaptchaHandler captchaHandler, ApplicationProperties env) {
        this.producer = producer;
        this.throwableTranslator = throwableTranslator;
        this.rabbitProducer = rabbitProducer;
        this.captchaHandler = captchaHandler;
        this.env = env;
    }

    public Mono<ServerResponse> logout(ServerRequest request) {
        String token = request.headers().firstHeader(Constants.HeaderNames.AUTHORIZATION);
        if (StringUtils.isBlank(token)) {
            token = request.queryParam("access_token").orElse(StringUtils.EMPTY);
        }

        if (StringUtils.isNotBlank(token)) {
            authorizationService.revokeToken(token);
            ValueCaches.delete(Constants.CacheNames.REFRESH_TOKEN, token);
            ValueCaches.delete(Constants.CacheNames.TOKEN_PLATFORM, token);
            ValueCaches.delete(Constants.CacheNames.TOKEN_USERNAME_ASSOCIATION, token);
        }

        return ServerResponse.noContent().build();
    }

    private void accumulateFailuresCount(String username) {
        Integer count = getFailuresCount(username);
        stringRedisTemplate.opsForValue().set(
                CACHE_KEY_FAILURES_COUNT + username,
                String.valueOf(++count),
                15,
                TimeUnit.MINUTES
        );
    }

    private Integer getFailuresCount(String username) {
        String value = stringRedisTemplate.opsForValue().get(CACHE_KEY_FAILURES_COUNT + username);
        return NumberUtils.isCreatable(value) ? NumberUtils.createInteger(value) : 0;
    }

    private Oauth2Token login(ServerRequest request, Credentials credentials, Platform platform) {
        String ipAddress = RequestUtils.getIpAddress(request);
        String regin = getIpRegion(ipAddress);
        getLogger().info("current login user is {},login ip {} regin is {}",credentials.getUsername(),ipAddress,regin);
        if (env.getDisabledAccessSources().stream().anyMatch(e->ipAddress.contains(e) || regin.contains(e))) {
            OperationLog log = createLog(request, platform);
            log.setResult(OperateResult.FAIL);
            //log.setDescription(String.format("账号【%s】受限地区访问",credentials.getUsername()));
            log.setArguments(credentials.toString());
            log.setResponse("受限地区访问");
            producer.produce(log);
            throw new AccessDeniedException(ErrorCode.E_0x000300009.getValue());
        }
        //白名单只做地区限制
        if (env.getEnabledAccessSources().stream().noneMatch(e->regin.contains(e))) {
            OperationLog log = createLog(request, platform);
            log.setResult(OperateResult.FAIL);
            log.setArguments(credentials.toString());
            log.setResponse("受限地区访问");
            producer.produce(log);
            throw new AccessDeniedException(ErrorCode.E_0x000300009.getValue());
        }
        try {
            int failuresCount = getFailuresCount(credentials.getUsername());
            if (failuresCount >= 5) {
                throw new AccessDeniedException(ErrorCode.E_0x000300007.getValue());
            }
            if (failuresCount >= 1 && Platform.needValidateCode(platform)) {
                if (StringUtils.isBlank(credentials.getCaptchaKey()) || StringUtils.isBlank(credentials.getCaptchaValue())) {
                    throw new AccessDeniedException(ErrorCode.E_0x000300008.getValue());
                }
                if (!captchaHandler.validate(credentials.getCaptchaKey(), credentials.getCaptchaValue())) {
                    throw new AccessDeniedException(ErrorCode.E_0x000300008.getValue());
                }
            }

            Oauth2Token token = authorizationService.getAccessToken(
                    GRANT_TYPE_PASSWORD,
                    ClientDetails.getInstance().getClientId(),
                    ClientDetails.getInstance().getClientSecret(),
                    credentials.getUsername(),
                    credentials.getPassword(),
                    platform
            );
            ValueCaches.put(Constants.CacheNames.REFRESH_TOKEN, token.getAccessToken(), token.getRefreshToken());
            ValueCaches.put(Constants.CacheNames.TOKEN_USERNAME_ASSOCIATION, token.getAccessToken(), credentials.getUsername());
            ValueCaches.put(Constants.CacheNames.TOKEN_PLATFORM, token.getAccessToken(), platform.name());
            onSuccess(request, platform, credentials);
            return token;
        } catch (Exception e) {
            accumulateFailuresCount(credentials.getUsername());
            onFailed(request, platform, credentials, e);
            throw e;
        }
    }

    public Mono<ServerResponse> validateScanCode(@NonNull ServerRequest request) {
        return request.bodyToMono(ScanCodeCredentials.class)
                .map(this::validateScanCode)
                .doOnNext(res -> rabbitProducer.sendRealMessage(res, ScanCodeHandler.ROUTEKEY_SCAN_CODE_RES))
                .flatMap(res -> ok().body(BodyInserters.fromValue(res)));
    }

    public Mono<ServerResponse> loginWithUsernameAndPassword(@NonNull ServerRequest request) {
        return request.bodyToMono(Credentials.class)
                .map(c -> login(request, c, Platform.WEB))
                .flatMap(token -> ok().body(BodyInserters.fromValue(new AccessToken(token.getAccessToken()))));
    }

    public Mono<ServerResponse> loginAppWithUsernameAndPassword(@NonNull ServerRequest request) {
        Credentials credentials = new Credentials();
        return request.bodyToMono(Credentials.class)
                .doOnNext(credentials::copy)
                .map(c -> login(request, c, Platform.APP))
                .flatMap(token -> {
                    Map<String, Object> res = new HashMap<>();
                    res.put("token", token.getAccessToken());
                    res.put("user", userService.findUserByUsername(credentials.getUsername()));
                    return ok().body(BodyInserters.fromValue(res));
                });
    }
    public Mono<ServerResponse> loginH5WithUsernameAndPassword(@NonNull ServerRequest request) {
        Credentials credentials = new Credentials();
        return request.bodyToMono(Credentials.class)
                .doOnNext(credentials::copy)
                .map(c -> login(request, c, Platform.H5))
                .flatMap(token -> {
                    Map<String, Object> res = new HashMap<>();
                    res.put("token", token.getAccessToken());
                    res.put("user", userService.findUserByUsername(credentials.getUsername()));
                    return ok().body(BodyInserters.fromValue(res));
                });
    }
    public Mono<ServerResponse> loginPCWithUsernameAndPassword(@NonNull ServerRequest request) {
        Credentials credentials = new Credentials();
        return request.bodyToMono(Credentials.class)
                .doOnNext(credentials::copy)
                .map(c -> login(request, c, Platform.PC))
                .flatMap(token -> {
                    Map<String, Object> res = new HashMap<>();
                    res.put("token", token.getAccessToken());
                    res.put("user", userService.findUserByUsername(credentials.getUsername()));
                    return ok().body(BodyInserters.fromValue(res));
                });
    }

    private ScanCodeResult validateScanCode(ScanCodeCredentials credentials) {
        log.debug("Receive credentials {}", credentials);
        ScanCodeResult res = ScanCodeResult.withCredentials(credentials);
        if (credentials.isExpired()) {
            return res.failure(ErrorCode.E_0x000300003);
        }
        String cacheKey = ScanCodeHandler.getCacheKey(credentials.getCode());
        String code = stringRedisTemplate.opsForValue().getAndDelete(cacheKey);
        if (StringUtils.isBlank(code)) {
            return res.failure(ErrorCode.E_0x000300004);
        }
        return res.success();
    }

    private OperationLog createLog(ServerRequest request, Platform platform) {
        OperationLog log = new OperationLog();
        log.setOperateTime(Instant.now());
        log.setDescription("用户名密码登录");
        log.setBusiness("用户登录");
        log.setChain(RequestUtils.getChain(request));
        log.setPlatform(platform.name());
        log.setUrl(request.path());
        log.setHttpMethod(request.methodName());
        log.setClientDescription(RequestUtils.getUserAgent(request));
        log.setIpAddress(RequestUtils.getIpAddress(request));
        return log;
    }

    private void onSuccess(ServerRequest request, Platform platform, Credentials credentials) {
        User user = userService.findUserByUsername(credentials.getUsername());
        OperationLog log = createLog(request, platform);
        log.setOperator(user.getId());
        log.setResult(OperateResult.SUCCESS);
        log.setArguments(credentials.toString());
        producer.produce(log);
    }

    private void onFailed(ServerRequest request, Platform platform, Credentials credentials, Throwable e) {
        OperationLog log = createLog(request, platform);
        log.setResult(OperateResult.FAIL);
        log.setArguments(credentials.toString());
        throwableTranslator.translate(e)
                .ifPresent(ex -> log.setResponse(DefaultResponse.withException(ex).URL(request.path()).build().toString()));
        producer.produce(log);
    }


    @Override
    protected Logger getLogger() {
        return log;
    }
}
