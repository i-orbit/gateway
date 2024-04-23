package com.inmaytide.orbit.gateway.configuration;

import com.inmaytide.orbit.gateway.handler.LoginWithScanCodeHandler;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author inmaytide
 * @since 2023/5/16
 */
@Configuration("customizedRabbitMQConfiguration")
public class RabbitMQConfiguration {

    @Bean("scanCodeResQueue")
    public Queue scanCodeResQueue() {
        return new Queue(LoginWithScanCodeHandler.ROUTE_KEY_SCAN_CODE_LOGIN_RES, true);
    }

    @Bean("bindingScanCodeResQueue")
    public Binding bindingNotify(@Qualifier("scanCodeResQueue") Queue queue,
                                 @Qualifier("directExchange") DirectExchange directExchange) {
        return BindingBuilder.bind(queue).to(directExchange).with(LoginWithScanCodeHandler.ROUTE_KEY_SCAN_CODE_LOGIN_RES);
    }

}
