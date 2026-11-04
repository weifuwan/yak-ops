package io.yak.ops.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

import lombok.Getter;

@Getter
public enum ScheduleMissedFirePolicy {

    SKIP_MISSED(0),
    FIRE_ONCE_NOW(1),
    FIRE_ALL_MISSED(2);

    @EnumValue
    private final int code;

    ScheduleMissedFirePolicy(int code) {
        this.code = code;
    }
}
