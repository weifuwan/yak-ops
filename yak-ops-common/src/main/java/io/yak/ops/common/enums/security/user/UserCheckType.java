package io.yak.ops.common.enums.security.user;

public enum UserCheckType {
    USER_NAME(1, "用户名"),
    USER_PHONE(2, "用户电话"),
    USER_MAIL(3, "用户邮箱");

    private int code;
    private String desc;

    private UserCheckType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return this.code;
    }

    public String getDesc() {
        return this.desc;
    }
}
