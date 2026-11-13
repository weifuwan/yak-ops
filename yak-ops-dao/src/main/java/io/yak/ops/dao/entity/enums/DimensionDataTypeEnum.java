package io.yak.ops.dao.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Data types supported by dimension table fields. DATE is normalized to DATE (legacy code had a malformed DATE( value). */
@Getter
@AllArgsConstructor
public enum DimensionDataTypeEnum {
    INT("INT", "INT"),
    BIGINT("BIGINT", "BIGINT"),
    BOOLEAN("BOOLEAN", "BOOLEAN"),
    STRING("STRING", "STRING"),
    TEXT("TEXT", "TEXT"),
    DECIMAL("DECIMAL", "DECIMAL"),
    DATE("DATE", "DATE"),
    DATETIME("DATETIME", "DATETIME"),
    TIMESTAMP("TIMESTAMP", "TIMESTAMP"),
    JSON("JSON", "JSON"),
    ARRAY("ARRAY", "ARRAY"),
    DOUBLE("DOUBLE", "DOUBLE"),
    MAP("MAP", "MAP"),
    STRUCT("STRUCT", "STRUCT");

    @EnumValue
    private final String code;
    private final String label;
}