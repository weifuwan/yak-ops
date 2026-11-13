package io.yak.ops.modeling.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DimensionFieldTypeEnum {
    PROPERTY("PROPERTY", "属性"),
    DIMENSION("DIMENSION", "维度"),
    MEASUREMENT("MEASUREMENT", "度量");

    @EnumValue
    private final String code;
    private final String name;
}