package com.inmaytide.orbit.gateway.domain;

import com.inmaytide.exception.web.BadCredentialsException;
import com.inmaytide.orbit.commons.consts.Is;
import com.inmaytide.orbit.commons.consts.Marks;
import com.inmaytide.orbit.commons.consts.Platforms;
import com.inmaytide.orbit.commons.utils.CodecUtils;
import com.inmaytide.orbit.gateway.configuration.ErrorCode;
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
public class Credentials implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(Credentials.class);

    @Serial
    private static final long serialVersionUID = 8244301757430198268L;

    private String username;

    private String password;

    private boolean rememberMe = false;

    private String captchaKey;

    private String captchaValue;

    private Platforms platform;

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
