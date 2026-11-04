package io.yak.ops.common.enums;

import java.util.HashMap;

import com.baomidou.mybatisplus.annotation.EnumValue;

import lombok.Getter;

@Getter
public enum WorkflowExecutionTypeEnum {

    PARALLEL(0, "parallel"),
    SERIAL_WAIT(1, "serial wait"),
    SERIAL_DISCARD(2, "serial discard"),
    SERIAL_PRIORITY(3, "serial priority");

    WorkflowExecutionTypeEnum(int code, String descp) {
        this.code = code;
        this.descp = descp;
    }

    @EnumValue
    private final int code;
    private final String descp;

    private static HashMap<Integer, WorkflowExecutionTypeEnum> EXECUTION_STATUS_MAP = new HashMap<>();

    static {
        for (WorkflowExecutionTypeEnum executionType : WorkflowExecutionTypeEnum.values()) {
            EXECUTION_STATUS_MAP.put(executionType.code, executionType);
        }
    }

    public static WorkflowExecutionTypeEnum of(int executionType) {
        if (EXECUTION_STATUS_MAP.containsKey(executionType)) {
            return EXECUTION_STATUS_MAP.get(executionType);
        }
        throw new IllegalArgumentException("invalid status : " + executionType);
    }

    public boolean isSerial() {
        return this != PARALLEL;
    }

}
