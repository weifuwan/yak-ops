package io.yak.ops.common.enums.task;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Durable submission and terminal lifecycle of one platform Task Attempt. */
@Getter
@RequiredArgsConstructor
public enum TaskAttemptStatus {
    PENDING("PENDING"),
    LAUNCHING("LAUNCHING"),
    RUNNING("RUNNING"),
    FINISHED("FINISHED"),
    FAILED("FAILED"),
    CANCELED("CANCELED"),
    INTERRUPTED("INTERRUPTED");

    @EnumValue
    private final String value;
}
