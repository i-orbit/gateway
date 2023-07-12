package com.inmaytide.orbit.gateway.configuration;

import com.inmaytide.orbit.gateway.domain.AccessToken;
import com.inmaytide.orbit.gateway.domain.Credentials;
import com.inmaytide.orbit.gateway.handler.CaptchaHandler;
import com.inmaytide.orbit.gateway.handler.LoginWithScanCodeHandler;
import com.inmaytide.orbit.gateway.handler.LoginWithUsernameAndPasswordHandler;
import com.inmaytide.orbit.gateway.handler.LogoutHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * @author luomiao
 * @since 2020/12/11
 */
@EnableWebFlux
@Configuration
public class WebConfiguration {

    private final LoginWithScanCodeHandler loginWithScanCodeHandler;

    private final LoginWithUsernameAndPasswordHandler loginWithUsernameAndPasswordHandler;

    private final CaptchaHandler captchaHandler;

    private final LogoutHandler logoutHandler;

    public WebConfiguration(LoginWithScanCodeHandler loginWithScanCodeHandler, LoginWithUsernameAndPasswordHandler loginWithUsernameAndPasswordHandler, CaptchaHandler captchaHandler, LogoutHandler logoutHandler) {
        this.loginWithScanCodeHandler = loginWithScanCodeHandler;
        this.loginWithUsernameAndPasswordHandler = loginWithUsernameAndPasswordHandler;
        this.captchaHandler = captchaHandler;
        this.logoutHandler = logoutHandler;
    }

    @Bean
    @RouterOperations({
            @RouterOperation(
                    path = "/authorize/login",
                    beanClass = LoginWithUsernameAndPasswordHandler.class,
                    beanMethod = "loginWithUsernameAndPassword"
            ),
            @RouterOperation(
                    path = "/authorize/captcha",
                    beanClass = CaptchaHandler.class,
                    beanMethod = "getCaptcha"
            ),
    })
    public RouterFunction<?> routers() {
        return route(RequestPredicates.POST("/authorize/login"), loginWithUsernameAndPasswordHandler::loginWithUsernameAndPassword)
                .andRoute(RequestPredicates.POST("/authorize/scan-code"), loginWithScanCodeHandler::validateScanCode)
                .andRoute(RequestPredicates.DELETE("/authorize/logout"), logoutHandler::logout)
                .andRoute(RequestPredicates.GET("/authorize/captcha"), captchaHandler::getCaptcha);
    }

    @Bean
    public HandlerMapping handlerMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/scan-code", loginWithScanCodeHandler);
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1);
        return mapping;
    }

}
