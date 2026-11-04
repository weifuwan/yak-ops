package io.yak.ops.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * complement data run mode
 */
public enum RunMode {

    /**
     * 0 serial run
     * 1 parallel run
     * */
    RUN_MODE_SERIAL(0, "serial run"),
    RUN_MODE_PARALLEL(1, "parallel run");

    RunMode(int code, String descp) {
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
