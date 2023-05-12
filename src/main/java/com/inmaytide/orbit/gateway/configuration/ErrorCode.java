package com.inmaytide.orbit.gateway.configuration;

/**
 * @author inmaytide
 * @since 2023/5/6
 */
public enum ErrorCode implements com.inmaytide.exception.web.domain.ErrorCode {

    E_0x00200001("0x00100001", "当前用户在其他地方登录, 您已被强制登出"),
    E_0x00200002("0x00200002", "访问被拒绝, 您目前没有访问该系统的权限"),
    E_0x00200003("0x00200003", "登录失败超过 {} 次, 请 {} 分钟后再试");

    private final String value;

    private final String description;

    ErrorCode(String value, String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    public String value() {
        return this.value;
    }

    @Override
    public String description() {
        return this.description;
    }
}
