package io.yak.ops.dao.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Doris physical data models supported by V1. AGGREGATE is intentionally deferred. */
@Getter
@AllArgsConstructor
public enum DorisDataModelEnum {
    DUPLICATE("DUPLICATE", "Duplicate Key"),
    UNIQUE("UNIQUE", "Unique Key");

    @EnumValue
    private final String code;
    private final String label;
}
