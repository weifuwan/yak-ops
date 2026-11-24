package io.yak.ops.dao.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DorisPartitionStrategyEnum {
    NONE("NONE", "不分区"),
    AUTO_RANGE("AUTO_RANGE", "自动范围分区");

    @EnumValue
    private final String code;
    private final String label;
}
