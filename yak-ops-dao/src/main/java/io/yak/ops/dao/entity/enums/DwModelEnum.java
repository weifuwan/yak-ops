package io.yak.ops.dao.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

import java.util.Arrays;

/** Warehouse model types and their supported layer/table combinations. */
@Getter
public enum DwModelEnum {
    SOURCE("SOURCE", "贴源表", "DIM", "SOURCE"),
    DIMENSION("DIMENSION", "维度表", "DWD,ADS", "DIMENSION"),
    DIM("DIM", "维度", "DWD,ADS", "DIM"),
    DIMENSION_DIM("DIMENSION_DIM", "维度表,维度", "DWD,ADS", "DIMENSION,DIM"),
    DETAIL("DETAIL", "明细表", "DWD", "DETAIL"),
    SUMMARY("SUMMARY", "汇总表", "DWD", "SUMMARY"),
    APPLICATION("APPLICATION", "应用表", "ADS", "APPLICATION");

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
     * Whether this layer record can contain the requested dimension table type.
     * The mode field may contain multiple exact values, such as DIMENSION,DIM.
     */
    public static boolean canCreate(DwModelEnum modelType, String tableType) {
        return modelType != null
                && tableType != null
                && Arrays.asList(modelType.getMode().split(",")).contains(tableType);
    }

    /** Whether this model type is valid for the selected warehouse layer. */
    public static boolean canCreate(DwTypeEnum layerType, DwModelEnum modelType) {
        return modelType != null && modelType.supportsLayer(layerType);
    }

    private boolean supportsLayer(DwTypeEnum layerType) {
        if (layerType == null) {
            return false;
        }
        switch (this) {
            case SOURCE:
                return layerType == DwTypeEnum.SOURCE_LAYER;
            case DIMENSION:
            case DIM:
            case DIMENSION_DIM:
                return layerType == DwTypeEnum.PUBLIC_LAYER
                        || layerType == DwTypeEnum.APPLICATION_LAYER;
            case DETAIL:
            case SUMMARY:
                return layerType == DwTypeEnum.PUBLIC_LAYER;
            case APPLICATION:
                return layerType == DwTypeEnum.APPLICATION_LAYER;
            default:
                return false;
        }
    }
}
