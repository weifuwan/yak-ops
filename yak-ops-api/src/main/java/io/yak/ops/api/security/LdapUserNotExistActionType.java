package io.yak.ops.api.security;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum LdapUserNotExistActionType {

    CREATE(0, "automatically create user when user not exist"),
    DENY(1, "deny log-in when user not exist"),
    ;

    LdapUserNotExistActionType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @EnumValue
    private final int code;
    private final String desc;
}
