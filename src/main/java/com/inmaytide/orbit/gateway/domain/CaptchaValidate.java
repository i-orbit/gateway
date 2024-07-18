package com.inmaytide.orbit.gateway.domain;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * @author inmaytide
 * @since 2024/7/18
 */
@Schema(title = "验证码验证接口参数")
public class CaptchaValidate implements Serializable {

    private String id;

    private ImageCaptchaTrack data;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ImageCaptchaTrack getData() {
        return data;
    }

    public void setData(ImageCaptchaTrack data) {
        this.data = data;
    }
}
