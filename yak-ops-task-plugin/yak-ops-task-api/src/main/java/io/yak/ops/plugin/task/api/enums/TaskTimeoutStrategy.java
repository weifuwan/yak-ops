package io.yak.ops.plugin.task.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum TaskTimeoutStrategy {

    /**
     * 0 warn
     * 1 failed
     * 2 warn+failed
     */
    WARN(0, "warn"),
    FAILED(1, "failed"),
    WARNFAILED(2, "warnfailed");

    TaskTimeoutStrategy(int code, String descp) {
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

    public static TaskTimeoutStrategy of(int status) {
        for (TaskTimeoutStrategy es : values()) {
            if (es.getCode() == status) {
                return es;
            }
        }
        throw new IllegalArgumentException("invalid status : " + status);
    }

}
