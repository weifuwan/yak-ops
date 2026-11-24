package io.yak.ops.dao.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DorisPartitionUnitEnum {
    DAY("DAY", "日"),
    WEEK("WEEK", "周"),
    MONTH("MONTH", "月"),
    QUARTER("QUARTER", "季度"),
    YEAR("YEAR", "年");

    @EnumValue
    private final String code;
    private final String label;
}
