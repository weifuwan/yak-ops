package io.yak.ops.dao.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;


@Getter
public enum DwModelEnum {
    SOURCE("SOURCE", "贴源表", "DIM", "SOURCE"),
    DIMENSION("DIMENSION", "维度表", "DWD,ADS", "DIMENSION"),
    DIM("DIM(", "维度", "DWD,ADS", "DIM"),
    DIMENSION_DIM("DIMENSION_DIM", "维度表,维度", "DWD,ADS", "DIMENSION,DIM"),
    DETAIL("DETAIL", "明细表", "DWD", "DETAIL"),
    SUMMARY("SUMMARY", "汇总表", "DWD", "SUMMARY"),
    APPLICATION("APPLICATION", "应用表", "ADS", "APPLICATION"),
    ;

    @EnumValue
    private final String code;

    private final String name;

    private final String type;

    private final String mode;

    DwModelEnum(String code, String name, String type, String mode) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.mode = mode;
    }

    /**
     * 该数仓分层是否可以创建该类模型
     */
    public static boolean canCreate(DwModelEnum modeType, String mode) {
        return modeType.getMode().contains(mode);
    }
}
