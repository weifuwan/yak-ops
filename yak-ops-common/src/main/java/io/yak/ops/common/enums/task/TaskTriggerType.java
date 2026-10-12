package io.yak.ops.common.enums.task;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Namespace for durable Task submission identities. */
@Getter
@RequiredArgsConstructor
public enum TaskTriggerType {
    MANUAL("MANUAL"),
    SCHEDULE("SCHEDULE"),
    WORKFLOW("WORKFLOW");

    @EnumValue
    private final String value;
}
