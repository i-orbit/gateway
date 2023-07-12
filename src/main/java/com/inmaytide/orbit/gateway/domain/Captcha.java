package com.inmaytide.orbit.gateway.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * @author inmaytide
 * @since 2023/7/12
 */
@Schema(title = "验证码信息")
public class Captcha implements Serializable {

    @Schema(title = "验证码存储标识")
    private String captchaKey;

    @Schema(title = "验证码图片", description = "Base64编码图片格式")
    private String image;

    public Captcha(String captchaKey, String image) {
        this.captchaKey = captchaKey;
        this.image = image;
    }

    public String getCaptchaKey() {
        return captchaKey;
    }

    public void setCaptchaKey(String captchaKey) {
        this.captchaKey = captchaKey;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
