package io.yak.ops.common.enums.task;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 一次 Task Attempt 的通用生命周期状态，失败重试产生新的 Attempt。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public enum AttemptStatus {
    PENDING(1),
    RUNNING(2),
    SUCCEEDED(3),
    FAILED(4),
    CANCELED(5),
    LOST(6);

    @EnumValue
    private final int value;

    AttemptStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
