package io.yak.ops.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

import lombok.Getter;

@Getter
public enum WorkerGroupSource {

    CONFIG(1, "config"),
    UI(2, "ui");

    @EnumValue
    private final int code;
    private final String desc;

    WorkerGroupSource(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
