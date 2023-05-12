package com.inmaytide.orbit.gateway.domain;

import java.io.Serializable;

/**
 * @author inmaytide
 * @since 2023/5/12
 */
public class AccessToken implements Serializable {

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
