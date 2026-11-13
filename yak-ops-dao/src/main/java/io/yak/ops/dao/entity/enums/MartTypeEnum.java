package io.yak.ops.dao.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 集市类型枚举类
 */
@Getter
public enum MartTypeEnum {

    PUBLIC("PUBLIC","公共集市"),
    BUSINESS("BUSINESS","业务集市"),
    APPLICATION("APPLICATION","数据应用集市"),
    ;
    @EnumValue
    private final String code;
    private final String name;

    MartTypeEnum(String code, String name) {
        this.name = name;
        this.code = code;
    }
}
