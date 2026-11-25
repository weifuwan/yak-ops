package io.yak.ops.dao.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** V1 publish strategies supported by the Doris physical-model workflow. */
@Getter
@AllArgsConstructor
public enum DorisPublishModeEnum {
    INCREMENTAL("INCREMENTAL", "增量发布"),
    DROP_RECREATE("DROP_RECREATE", "删除重建");

    @EnumValue
    private final String code;
    private final String label;
}