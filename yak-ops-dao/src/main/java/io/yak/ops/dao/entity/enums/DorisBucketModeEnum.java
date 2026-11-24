package io.yak.ops.dao.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DorisBucketModeEnum {
    AUTO("AUTO", "自动分桶"),
    FIXED("FIXED", "固定分桶");

    @EnumValue
    private final String code;
    private final String label;
}
