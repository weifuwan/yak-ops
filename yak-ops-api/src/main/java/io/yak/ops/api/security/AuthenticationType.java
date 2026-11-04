package io.yak.ops.api.security;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum AuthenticationType {

    PASSWORD(0, "verify via user name and password"),
    LDAP(1, "verify via LDAP server"),
    CASDOOR_SSO(2, "verify via casdoor sso provider"),
    OIDC(3, "verify via OpenID Connect provider");

    AuthenticationType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @EnumValue
    private final int code;
    private final String desc;
}
