package io.yak.ops.modeling.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 数仓分层类型
 *
 */
@Getter
public enum DwTypeEnum {

    SOURCE_LAYER("SOURCE_LAYER","贴源层"),
    PUBLIC_LAYER("PUBLIC_LAYER","公共层"),
    APPLICATION_LAYER("APPLICATION_LAYER","应用层"),
    NONE_LAYER("NONE_LAYER","未分层");

    @EnumValue
    private final String code;
    private final String name;

    DwTypeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
