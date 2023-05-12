package com.inmaytide.orbit.gateway.handler;

import com.carrot.commons.business.dto.Oauth2Token;
import com.carrot.commons.business.dto.User;
import com.carrot.commons.consts.Constants;
import com.carrot.commons.consts.OperateResult;
import com.carrot.commons.consts.Platform;
import com.carrot.commons.log.LogMessageProducer;
import com.carrot.commons.log.domain.OperationLog;
import com.carrot.commons.security.ClientDetails;
import com.carrot.commons.service.authorization.AuthorizationService;
import com.carrot.commons.utils.ValueCaches;
import com.carrot.gateway.config.ErrorCode;
import com.carrot.gateway.domain.ScanCodeResult;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * @author inmaytide
 * @since 2022/9/9
 */
@Component
@RabbitListener(queues = ScanCodeHandler.ROUTEKEY_SCAN_CODE_RES)
public class ScanCodeResultConsumer extends AbstractHandler {

    private static final Logger log = LoggerFactory.getLogger(ScanCodeResultConsumer.class);

    private final AuthorizationService authorizationService;

    private final LogMessageProducer logMessageProducer;

    public ScanCodeResultConsumer(AuthorizationService authorizationService, LogMessageProducer logMessageProducer) {
        this.authorizationService = authorizationService;
        this.logMessageProducer = logMessageProducer;
    }

    @RabbitHandler
    public void onReceiveScanCodeResult(@Payload ScanCodeResult res, Message message, Channel channel) {
        log.debug("Receive a scan code result, content is {}", res.toFullString());
        ScanCodeHandler.WebSocketSender sender = ScanCodeHandler.getSender(res.getSessionId());
        if (sender == null) {
            log.debug("Receive a scan code result, but there is no websocket session in application.");
            return;
        }

        if ("success".equals(res.getResult())) {
            log.debug("QR code verification succeeded, Perform login operation, get access_token...");
            try {
                execute(res, sender);
            } catch (Exception e) {
                res.failure(ErrorCode.E_0x000300005);
                log.error("An error occurred while performing the login, Cause by: ", e);
            }
        }
        sender.send(res.toString());
    }

    private void execute(ScanCodeResult res, ScanCodeHandler.WebSocketSender sender) {
        Oauth2Token token = authorizationService.getAccessToken(
                AuthorizeHandler.GRANT_TYPE_PASSWORD,
                ClientDetails.getInstance().getClientId(),
                ClientDetails.getInstance().getClientSecret(),
                res.getUsername(),
                Constants.Marks.LOGIN_WITHOUT_PASSWORD,
                Platform.WEB
        );
        User user = userService.findUserByUsername(res.getUsername());
        ValueCaches.put(Constants.CacheNames.REFRESH_TOKEN, token.getAccessToken(), token.getRefreshToken());
        ValueCaches.put(Constants.CacheNames.TOKEN_USERNAME_ASSOCIATION, token.getAccessToken(), res.getUsername());
        ValueCaches.put(Constants.CacheNames.TOKEN_PLATFORM, token.getAccessToken(), Platform.WEB.name());
        OperationLog log = new OperationLog();
        log.setOperateTime(Instant.now());
        log.setDescription("APP扫码登录");
        log.setBusiness("用户登录");
        log.setPlatform(Platform.WEB.name());
        log.setUrl(sender.getSession().getHandshakeInfo().getUri().toString());
        log.setHttpMethod("WebSocket");
        log.setClientDescription(sender.getSession().getHandshakeInfo().getHeaders().getFirst("User-Agent"));
        log.setIpAddress(sender.getSession().getHandshakeInfo().getRemoteAddress().getAddress().getHostAddress());
        log.setOperator(user.getId());
        log.setResult(OperateResult.SUCCESS);
        logMessageProducer.produce(log);
        res.setAccessToken(token.getAccessToken());
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
}
