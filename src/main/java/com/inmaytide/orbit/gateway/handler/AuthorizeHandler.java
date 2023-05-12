package com.inmaytide.orbit.gateway.handler;

import com.inmaytide.exception.translator.ThrowableTranslator;
import com.inmaytide.exception.web.HttpResponseException;
import com.inmaytide.orbit.commons.consts.Platforms;
import com.inmaytide.orbit.commons.domain.Oauth2Token;
import com.inmaytide.orbit.gateway.configuration.ApplicationProperties;
import com.inmaytide.orbit.gateway.domain.Credentials;
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
public class AuthorizeHandler extends AbstractHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthorizeHandler.class);

    public static final String GRANT_TYPE_PASSWORD = "password";

    private static final String CACHE_KEY_FAILURES_COUNT = "FAILURES_COUNT::";



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



    public Mono<ServerResponse> validateScanCode(@NonNull ServerRequest request) {
        return request.bodyToMono(ScanCodeCredentials.class)
                .map(this::validateScanCode)
                .doOnNext(res -> rabbitProducer.sendRealMessage(res, ScanCodeHandler.ROUTEKEY_SCAN_CODE_RES))
                .flatMap(res -> ok().body(BodyInserters.fromValue(res)));
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




    @Override
    protected Logger getLogger() {
        return log;
    }
}
