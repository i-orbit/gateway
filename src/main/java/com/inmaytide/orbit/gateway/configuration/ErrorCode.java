package com.inmaytide.orbit.gateway.configuration;

/**
 * @author inmaytide
 * @since 2023/5/6
 */
public enum ErrorCode implements com.inmaytide.exception.web.domain.ErrorCode {

    E_0x00200001("0x00200001", "您的账户在另一地点登录, 您已被迫下线"),
    E_0x00200002("0x00200002", "访问被拒绝, 您目前没有访问该系统的权限"),
    E_0x00200003("0x00200003", "登录失败超过 {0} 次, 请 {1} 分钟后再试"),
    E_0x00200004("0x00200004", "二维码已过期"),
    E_0x00200005("0x00200005", "二维码已被使用"),
    E_0x00200006("0x00200006", "二维码验证成功，但执行登录时发生位置错误, 请稍后再试或联系系统管理员"),
    E_0x00200007("0x00200007", "验证码输入错误"),
    E_0x00200008("0x00200008", "生成验证码时发生错误"),
    E_0x00200009("0x00200009", "用户名、密码、登录平台等请求参数不能为空"),
    E_0x00200010("0x00200010", "用户名或密码输入错误"),
    ;

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
