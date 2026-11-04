package io.yak.ops.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum TimeoutFlag {

    /**
     * 0 close
     * 1 open
     */
    CLOSE(0, "close"),
    OPEN(1, "open");

    TimeoutFlag(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @EnumValue
    private final int code;
    private final String desc;

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
