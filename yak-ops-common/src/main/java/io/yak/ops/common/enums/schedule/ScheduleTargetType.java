package io.yak.ops.common.enums.schedule;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Supported root objects for periodic submission, independent of Task plugin type. */
@Getter
@RequiredArgsConstructor
public enum ScheduleTargetType {
    TASK("TASK");

    @EnumValue
    private final String value;
}
