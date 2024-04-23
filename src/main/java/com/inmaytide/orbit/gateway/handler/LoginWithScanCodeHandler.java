package com.inmaytide.orbit.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inmaytide.exception.web.translator.HttpExceptionTranslatorDelegator;
import com.inmaytide.orbit.commons.log.OperationLogMessageProducer;
import com.inmaytide.orbit.commons.service.uaa.UserService;
import com.inmaytide.orbit.commons.utils.CodecUtils;
import com.inmaytide.orbit.commons.utils.ValueCaches;
import com.inmaytide.orbit.commons.utils.producer.RabbitProducer;
import com.inmaytide.orbit.gateway.configuration.ApplicationProperties;
import com.inmaytide.orbit.gateway.configuration.ErrorCode;
import com.inmaytide.orbit.gateway.domain.ScanCodeCredentials;
import com.inmaytide.orbit.gateway.domain.ScanCodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

/**
 * @author inmaytide
 * @since 2022/9/8
 */
@Component
public class LoginWithScanCodeHandler extends AbstractAuthorizeHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginWithScanCodeHandler.class);

    private static final Map<String, WebSocketSender> SENDER_STORE = new ConcurrentHashMap<>();

    public static final String REQ_NEW_CODE = "getNewCode";

    public static final String CACHE_NAME_SCAN_CODE = "SCAN_CODE";

    public static final String ROUTE_KEY_SCAN_CODE_LOGIN_RES = "orbit.queue.scan.code.login.result";

    public static final Long CODE_VALID_TIME_IN_SECONDS = 10L * 60;

    private final RabbitProducer rabbitProducer;

    private final ObjectMapper objectMapper;

    protected LoginWithScanCodeHandler(OperationLogMessageProducer producer, ApplicationProperties properties, HttpExceptionTranslatorDelegator throwableTranslator, UserService userService, CaptchaHandler captchaHandler, RabbitProducer rabbitProducer, ObjectMapper objectMapper) {
        super(producer, properties, throwableTranslator, userService, captchaHandler);
        this.rabbitProducer = rabbitProducer;
        this.objectMapper = objectMapper;
    }


    private ScanCodeResult validateScanCode(ScanCodeCredentials credentials) {
        log.debug("Receive credentials {}", credentials);
        ScanCodeResult res = ScanCodeResult.withCredentials(credentials);
        if (credentials.isExpired()) {
            return res.failure(ErrorCode.E_0x00200004);
        }
        return ValueCaches.getAndDelete(CACHE_NAME_SCAN_CODE, credentials.getCode())
                .map(e -> res.success())
                .orElse(res.failure(ErrorCode.E_0x00200005));
    }

    public Mono<ServerResponse> validateScanCode(@NonNull ServerRequest request) {
        return request.bodyToMono(ScanCodeCredentials.class)
                .map(this::validateScanCode)
                .doOnNext(res -> rabbitProducer.sendMessage(res, ROUTE_KEY_SCAN_CODE_LOGIN_RES))
                .flatMap(res -> ok().body(BodyInserters.fromValue(res)));
    }

    @Override
    public @NonNull Mono<Void> handle(@NonNull WebSocketSession session) {
        Mono<Void> in = session.receive()
                .doOnNext(message -> processMessage(session, message))
                .doFinally(signal -> disconnect(session, signal))
                .then();

        Mono<Void> out = session.send(Flux.create(sink -> {
            SENDER_STORE.put(session.getId(), new WebSocketSender(session, sink));
        }));
        return Mono.zip(in, out).then();
    }

    private void disconnect(WebSocketSession session, SignalType signal) {
        log.debug("Terminating WebSocket Session (client side) signal: [{}], [{}]", signal.name(), session.getId());
        SENDER_STORE.remove(session.getId());
        session.close();
    }

    private void processMessage(WebSocketSession session, WebSocketMessage message) {
        log.debug("Receive a new message from client[{}], content is [{}]", session.getId(), message.getPayloadAsText());
        String content = message.getPayloadAsText();
        if (REQ_NEW_CODE.equals(content)) {
            try {
                String code = CodecUtils.generateRandomCode(8);
                ValueCaches.put(CACHE_NAME_SCAN_CODE, code, code, CODE_VALID_TIME_IN_SECONDS, TimeUnit.SECONDS);
                String res = session.getId() + "." + code + "." + Instant.now().plusSeconds(CODE_VALID_TIME_IN_SECONDS).toEpochMilli();
                Map<String, String> body = Map.of("content", CodecUtils.encrypt(res, CodecUtils.RSA_PRIVATE_KEY), "category", "login");
                log.debug("Generated new code content is [{}]", res);
                SENDER_STORE.get(session.getId()).send(objectMapper.writeValueAsString(body));
                log.debug("Generated new code was sent");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nullable
    public static WebSocketSender getSender(String id) {
        return SENDER_STORE.get(id);
    }

    @Override
    protected Logger getLogger() {
        return log;
    }


    public static class WebSocketSender {

        private final WebSocketSession session;

        private final FluxSink<WebSocketMessage> sink;

        public WebSocketSender(WebSocketSession session, FluxSink<WebSocketMessage> sink) {
            this.session = session;
            this.sink = sink;
        }

        public void send(String message) {
            sink.next(session.textMessage(message));
        }

        public WebSocketSession getSession() {
            return session;
        }
    }


}
