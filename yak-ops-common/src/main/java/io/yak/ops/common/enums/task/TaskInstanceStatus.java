package io.yak.ops.common.enums.task;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Persistent lifecycle of one requested business Task execution. */
@Getter
@RequiredArgsConstructor
public enum TaskInstanceStatus {
    SUBMITTED("SUBMITTED"),
    RUNNING("RUNNING"),
    CANCELLING("CANCELLING"),
    RECONCILING("RECONCILING"),
    FINISHED("FINISHED"),
    FAILED("FAILED"),
    CANCELED("CANCELED");

    @EnumValue
    private final String value;

    public boolean isTerminal() {
        return this == FINISHED || this == FAILED || this == CANCELED;
    }
}
