package io.yak.ops.dao.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DorisDistributionTypeEnum {
    HASH("HASH", "Hash");

    @EnumValue
    private final String code;
    private final String label;
}
