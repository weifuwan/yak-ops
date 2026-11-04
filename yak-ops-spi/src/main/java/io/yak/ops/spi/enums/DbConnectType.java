package io.yak.ops.spi.enums;

import lombok.Getter;

@Getter
public enum DbConnectType {

    ORACLE_SERVICE_NAME(0, "Oracle Service Name"),
    ORACLE_SID(1, "Oracle SID");

    DbConnectType(int code, String descp) {
        this.code = code;
        this.descp = descp;
    }

    private final int code;

    private final String descp;

}
