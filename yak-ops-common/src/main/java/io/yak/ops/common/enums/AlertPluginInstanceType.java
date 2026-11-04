package io.yak.ops.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum AlertPluginInstanceType {

    NORMAL(0, "NORMAL"),
    GLOBAL(1, "GLOBAL");
    AlertPluginInstanceType(int code, String descp) {
        this.code = code;
        this.descp = descp;
    }

    @EnumValue
    private final int code;
    private final String descp;

    public int getCode() {
        return code;
    }

    public String getDescp() {
        return descp;
    }

}
