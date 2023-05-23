package com.inmaytide.orbit.gateway.domain;

import com.inmaytide.exception.web.BadCredentialsException;
import com.inmaytide.orbit.commons.consts.Is;
import com.inmaytide.orbit.commons.consts.Marks;
import com.inmaytide.orbit.commons.consts.Platforms;
import com.inmaytide.orbit.commons.utils.CodecUtils;
import com.inmaytide.orbit.gateway.configuration.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * @author inmaytide
 * @since 2023/5/12
 */
@Schema(title = "通过用户名密码登录请求实体")
public class Credentials implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(Credentials.class);

    @Serial
    private static final long serialVersionUID = 8244301757430198268L;

    @NotBlank
    @Schema(title = "用户登录名")
    private String username;

    @NotBlank
    @Schema(title = "用户登录密码", description = "需要传输通过RSA加密后的字符串")
    private String password;

    @Schema(title = "记住我")
    private boolean rememberMe = false;

    @Schema(title = "验证码存储缓存KEY", description = "登录尝试失败超过一次后必填")
    private String captchaKey;

    @Schema(title = "验证码", description = "登录尝试失败超过一次后必填")
    private String captchaValue;

    @Schema(title = "客户端平台")
    private Platforms platform;

    @Schema(title = "强制登录", description = "当用户在同客户端平台其他位置已登录时, 是否强制其他位置的登录下线")
    private Is forcedReplacement;

    public void validate() {
        if (StringUtils.isBlank(username)
                || StringUtils.isBlank(password)
                || platform == null) {
            throw new BadCredentialsException(ErrorCode.E_0x00200009);
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        try {
            return CodecUtils.decrypt(password, CodecUtils.RSA_PUBLIC_KEY);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.error("Password of User{username = {}} decryption failed, Cause by: ", getPassword(), e);
            } else {
                log.warn("Password of User{username = {}} decryption failed", getUsername());
            }
        }
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }

    public String getCaptchaKey() {
        return captchaKey;
    }

    public void setCaptchaKey(String captchaKey) {
        this.captchaKey = captchaKey;
    }

    public String getCaptchaValue() {
        return captchaValue;
    }

    public void setCaptchaValue(String captchaValue) {
        this.captchaValue = captchaValue;
    }

    public Platforms getPlatform() {
        return platform;
    }

    public void setPlatform(Platforms platform) {
        this.platform = platform;
    }

    public Is getForcedReplacement() {
        return Objects.requireNonNullElse(forcedReplacement, Is.N);
    }

    public void setForcedReplacement(Is forcedReplacement) {
        this.forcedReplacement = forcedReplacement;
    }

    @Override
    public String toString() {
        return """
                Credentials{username='%s', password='N/A', rememberMe=%s, platform='%s'}
                """.formatted(
                getUsername(),
                isRememberMe(),
                platform == null ? Marks.NOT_APPLICABLE.getValue() : platform.name()
        );
    }


}
