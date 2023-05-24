package com.inmaytide.orbit.gateway.handler;

import com.inmaytide.exception.translator.ThrowableTranslator;
import com.inmaytide.exception.web.HttpResponseException;
import com.inmaytide.orbit.commons.consts.Is;
import com.inmaytide.orbit.commons.consts.Marks;
import com.inmaytide.orbit.commons.consts.Platforms;
import com.inmaytide.orbit.commons.domain.Oauth2Token;
import com.inmaytide.orbit.commons.domain.OrbitClientDetails;
import com.inmaytide.orbit.commons.log.OperateResult;
import com.inmaytide.orbit.commons.log.OperationLogMessageProducer;
import com.inmaytide.orbit.commons.log.domain.OperationLog;
import com.inmaytide.orbit.commons.service.uaa.AuthorizationService;
import com.inmaytide.orbit.commons.service.uaa.UserService;
import com.inmaytide.orbit.commons.utils.HttpUtils;
import com.inmaytide.orbit.gateway.configuration.ApplicationProperties;
import com.inmaytide.orbit.gateway.configuration.ErrorCode;
import com.inmaytide.orbit.gateway.domain.ScanCodeResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.HandshakeInfo;

import java.time.Instant;

/**
 * @author inmaytide
 * @since 2022/9/9
 */
@Component
@RabbitListener(queues = LoginWithScanCodeHandler.ROUTE_KEY_SCAN_CODE_LOGIN_RES)
public class LoginWithScanCodeResultConsumer extends AbstractAuthorizeHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginWithScanCodeResultConsumer.class);

    protected LoginWithScanCodeResultConsumer(OperationLogMessageProducer producer, ApplicationProperties properties, ThrowableTranslator<HttpResponseException> throwableTranslator, UserService userService, CaptchaHandler captchaHandler) {
        super(producer, properties, throwableTranslator, userService, captchaHandler);
    }


    @RabbitHandler
    public void onReceiveScanCodeResult(@Payload ScanCodeResult res) {
        log.debug("Receive a scan code result, content is {}", res.toFullString());
        LoginWithScanCodeHandler.WebSocketSender sender = LoginWithScanCodeHandler.getSender(res.getSessionId());
        if (sender == null) {
            log.debug("Receive a scan code result, but there is no websocket session in application.");
            return;
        }

        if ("success".equals(res.getResult())) {
            log.debug("QR code verification succeeded, Perform login operation, get access_token...");
            try {
                execute(res, sender);
            } catch (Exception e) {
                res.failure(ErrorCode.E_0x00200006);
                log.error("An error occurred while performing the login, Cause by: ", e);
            }
        }
        sender.send(res.toString());
    }

    private void execute(ScanCodeResult res, LoginWithScanCodeHandler.WebSocketSender sender) {
        Oauth2Token token = authorizationService.getToken(
                res.getUsername(),
                Marks.LOGIN_WITHOUT_PASSWORD.getValue(),
                Platforms.WEB,
                Is.Y
        );
        OperationLog log = new OperationLog();
        log.setOperateTime(Instant.now());
        log.setDescription("APP扫码登录");
        log.setBusiness("用户登录");
        log.setPlatform(Platforms.WEB.name());
        log.setUrl(sender.getSession().getHandshakeInfo().getUri().toString());
        log.setHttpMethod("WebSocket");
        log.setClientDescription(sender.getSession().getHandshakeInfo().getHeaders().getFirst("User-Agent"));
        log.setIpAddress(getClientIpAddress(sender.getSession().getHandshakeInfo()));
        log.setResult(OperateResult.SUCCESS);
        userService.getIdByUsername(res.getUsername()).ifPresent(log::setOperator);
        producer.produce(log);
        res.setAccessToken(token.getAccessToken());
    }

    private String getClientIpAddress(HandshakeInfo handshakeInfo) {
        for (String name : HttpUtils.HEADER_NAMES_FOR_CLIENT_ID) {
            String value = handshakeInfo.getHeaders().getFirst(name);
            if (StringUtils.isNotBlank(value) && !StringUtils.equalsIgnoreCase("unknown", value)) {
                return HttpUtils.getIpAddress(value);
            }
        }
        return handshakeInfo.getRemoteAddress() == null ? "" : handshakeInfo.getRemoteAddress().getAddress().getHostAddress();
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
}
