package com.inmaytide.orbit.gateway.handler;

import com.carrot.commons.utils.CommonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
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

/**
 * @author inmaytide
 * @since 2022/9/8
 */
@Component
public class ScanCodeHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ScanCodeHandler.class);

    private static final Map<String, WebSocketSender> SENDER_STORE = new ConcurrentHashMap<>();

    public static final String REQ_NEW_CODE = "getNewCode";

    public static final String CACHE_NAME_SCAN_CODE = "SCAN_CODE";

    public static final String ROUTEKEY_SCAN_CODE_RES = "carrot.queue.scan.code.result";

    public static final Long CODE_VALID_TIME_IN_SECONDS = 10L * 60;

    private final StringRedisTemplate template;

    private final ObjectMapper objectMapper;

    public ScanCodeHandler(StringRedisTemplate template, ObjectMapper objectMapper) {
        this.template = template;
        this.objectMapper = objectMapper;
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
            String code = CommonUtils.generateRandomCode(8);
            template.opsForValue().set(getCacheKey(code), code, CODE_VALID_TIME_IN_SECONDS, TimeUnit.SECONDS);
            String res = session.getId() + "." + code + "." + Instant.now().plusSeconds(CODE_VALID_TIME_IN_SECONDS).toEpochMilli();
            Map<String, String> body = Map.of("content", CommonUtils.encrypt(res, RSA_PUBLIC_KEY), "category", "login");
            log.debug("Generated new code content is [{}]", res);
            try {
                SENDER_STORE.get(session.getId()).send(objectMapper.writeValueAsString(body));
                log.debug("Generated new code was sent");
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static String getCacheKey(String code) {
        return CACHE_NAME_SCAN_CODE + "::" + code;
    }

    @Nullable
    public static WebSocketSender getSender(String id) {
        return SENDER_STORE.get(id);
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
