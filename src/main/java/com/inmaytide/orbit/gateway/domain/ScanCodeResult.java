package com.inmaytide.orbit.gateway.domain;

import com.inmaytide.orbit.gateway.configuration.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author inmaytide
 * @since 2022/9/8
 */
@Schema(title = "扫码登录二维码验证结果")
public class ScanCodeResult implements Serializable {

    @Serial
    private static final long serialVersionUID = -7082431747878098210L;

    @Schema(title = "Websocket Session标识")
    private String sessionId;

    @Schema(title = "登录的用户名")
    private String username;

    @Schema(title = "验证结果")
    private String result;

    @Schema(title = "失败原因")
    private String message;

    @Schema(title = "验证成功后返回的 AccessToken")
    private String accessToken;

    private String refreshToken;

    public static ScanCodeResult withCredentials(ScanCodeCredentials credentials) {
        ScanCodeResult res = new ScanCodeResult();
        res.setSessionId(credentials.getSessionId());
        res.setUsername(credentials.getUsername());
        return res;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public ScanCodeResult failure(ErrorCode errorCode) {
        this.result = "failure";
        this.message = errorCode.description();
        return this;
    }

    public ScanCodeResult success() {
        this.result = "success";
        return this;
    }

    @Override
    public String toString() {
        if ("failure".equals(getResult())) {
            return String.format("{\"result\": \"%s\", \"message\": \"%s\"}", getResult(), getMessage());
        }
        return String.format("{\"result\": \"%s\", \"accessToken\": \"%s\"}", getResult(), getAccessToken());
    }

    public String toFullString() {
        return "ScanCodeResult{" +
                "sessionId='" + sessionId + '\'' +
                ", username='" + username + '\'' +
                ", result='" + result + '\'' +
                ", message='" + message + '\'' +
                ", accessToken='" + accessToken + '\'' +
                '}';
    }
}
