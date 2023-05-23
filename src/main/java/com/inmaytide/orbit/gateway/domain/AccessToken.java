package com.inmaytide.orbit.gateway.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * @author inmaytide
 * @since 2023/5/12
 */
@Schema(title = "登录成功返回实体")
public class AccessToken implements Serializable {

    @Schema(title = "访问后端接口请求凭证")
    private String accessToken;

    public AccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
