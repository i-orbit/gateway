package com.inmaytide.orbit.gateway.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.inmaytide.orbit.commons.utils.CodecUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.Serializable;
import java.time.Instant;

/**
 * @author inmaytide
 * @since 2022/9/8
 */
@Schema(title = "扫码登录二维码内容验证")
public class ScanCodeCredentials implements Serializable {

    private static final long serialVersionUID = -8555725055594984597L;

    @Schema(title = "二维码内容")
    private String content;

    @Schema(title = "要登录的用户用户名")
    private String username;

    @JsonIgnore
    private String sessionId;

    @JsonIgnore
    private String code;

    @JsonIgnore
    private Instant expireAt;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        try {
            String[] values = StringUtils.split(CodecUtils.decrypt(content, CodecUtils.RSA_PRIVATE_KEY), ".");
            this.sessionId = values[0];
            this.code = values[1];
            this.expireAt = Instant.ofEpochMilli(NumberUtils.createLong(values[2]));
            if (values.length != 3) { // 二维码内容不正确
                throw new IllegalArgumentException(content);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(content);
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCode() {
        return code;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public boolean isExpired() {
        return expireAt.isBefore(Instant.now());
    }

    @Override
    public String toString() {
        return "ScanCodeCredentials{" +
                "content='" + content + '\'' +
                ", username='" + username + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", code='" + code + '\'' +
                ", expireAt=" + expireAt +
                '}';
    }
}
