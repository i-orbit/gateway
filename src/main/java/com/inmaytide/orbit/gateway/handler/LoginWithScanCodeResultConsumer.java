package com.inmaytide.orbit.gateway.handler;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import com.inmaytide.exception.web.translator.HttpExceptionTranslatorDelegator;
import com.inmaytide.orbit.commons.constants.Bool;
import com.inmaytide.orbit.commons.constants.Constants;
import com.inmaytide.orbit.commons.constants.Platforms;
import com.inmaytide.orbit.commons.domain.Oauth2Token;
import com.inmaytide.orbit.commons.domain.dto.params.LoginParameters;
import com.inmaytide.orbit.commons.log.OperationLogMessageProducer;
import com.inmaytide.orbit.commons.log.domain.OperationLog;
import com.inmaytide.orbit.commons.service.uaa.UserService;
import com.inmaytide.orbit.commons.utils.HttpUtils;
import com.inmaytide.orbit.gateway.configuration.ApplicationProperties;
import com.inmaytide.orbit.gateway.configuration.ErrorCode;
import com.inmaytide.orbit.gateway.domain.ScanCodeResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.HandshakeInfo;

/**
 * @author inmaytide
 * @since 2022/9/9
 */
@Component
public class LoginWithScanCodeResultConsumer extends AbstractAuthorizeHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginWithScanCodeResultConsumer.class);

    protected LoginWithScanCodeResultConsumer(OperationLogMessageProducer producer, ApplicationProperties properties, HttpExceptionTranslatorDelegator throwableTranslator, UserService userService, ImageCaptchaApplication captchaApplication) {
        super(producer, properties, throwableTranslator, userService, captchaApplication);
    }


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(LoginWithScanCodeHandler.ROUTE_KEY_SCAN_CODE_LOGIN_RES),
            exchange = @Exchange(value = Constants.RabbitMQ.DIRECT_EXCHANGE),
            key = LoginWithScanCodeHandler.ROUTE_KEY_SCAN_CODE_LOGIN_RES
    ))
    public void onReceiveScanCodeResult(ScanCodeResult res) {
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
        // Perform login without password operation to obtain token
        LoginParameters params = new LoginParameters();
        params.setLoginName(res.getUsername());
        params.setForcedReplacement(Bool.Y);
        params.setPassword(Constants.Markers.LOGIN_WITHOUT_PASSWORD);
        params.setPlatform(Platforms.WEB);
        Oauth2Token token = authorizationService.getToken(params);

        // Record operation log
        OperationLog log = new OperationLog();
        log.setDescription("APP扫码登录");
        log.setBusiness("用户登录");
        log.setPlatform(Platforms.WEB.name());
        log.setPath(sender.getSession().getHandshakeInfo().getUri().toString());
        log.setHttpMethod("WebSocket");
        log.setClientDescription(sender.getSession().getHandshakeInfo().getHeaders().getFirst("User-Agent"));
        log.setIpAddress(getClientIpAddress(sender.getSession().getHandshakeInfo()));
        log.setResult(Bool.Y);
        log.setOperator(token.getUserId());
        log.setTenantId(token.getTenant());
        producer.produce(log);
        res.setAccessToken(token.getAccessToken());
        res.setRefreshToken(token.getRefreshToken());
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
