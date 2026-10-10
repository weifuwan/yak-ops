package io.yak.ops.common.enums.task;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 任务根实例的触发来源，RETRY 编码仅保留历史记录。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public enum TriggerType {
    MANUAL(1),
    SCHEDULE(2),
    RETRY(3),
    AUTO_RECOVERY(4);

    @EnumValue
    private final int value;

    TriggerType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
