package io.yak.ops.common.enums.task;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * Task Instance 生命周期状态，对齐已发布的数据同步状态持久化编码。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public enum InstanceStatus {
    PENDING(1),
    RUNNING(2),
    SUCCEEDED(3),
    FAILED(4),
    CANCELED(5),
    LOST(6),
    RETRY_WAITING(7);

    @EnumValue
    private final int value;

    InstanceStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
