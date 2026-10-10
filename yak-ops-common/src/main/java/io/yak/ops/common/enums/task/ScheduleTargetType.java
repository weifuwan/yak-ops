package io.yak.ops.common.enums.task;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 通用 Schedule 的目标类型；WORKFLOW 当前仅为持久化边界，尚未实现调度执行。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public enum ScheduleTargetType {
    TASK("TASK"),
    WORKFLOW("WORKFLOW");

    @EnumValue
    private final String value;

    ScheduleTargetType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
