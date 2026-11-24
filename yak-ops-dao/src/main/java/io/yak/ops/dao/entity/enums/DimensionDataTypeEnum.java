package io.yak.ops.dao.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Data types supported by dimension table fields. DATE is normalized to DATE (legacy code had a malformed DATE( value). */
@Getter
@AllArgsConstructor
public enum DimensionDataTypeEnum {
    TINYINT("TINYINT", "TINYINT"),
    SMALLINT("SMALLINT", "SMALLINT"),
    INT("INT", "INT"),
    BIGINT("BIGINT", "BIGINT"),
    BOOLEAN("BOOLEAN", "BOOLEAN"),
    CHAR("CHAR", "CHAR"),
    VARCHAR("VARCHAR", "VARCHAR"),
    STRING("STRING", "STRING"),
    TEXT("TEXT", "TEXT"),
    DECIMAL("DECIMAL", "DECIMAL"),
    DATE("DATE", "DATE"),
    DATETIME("DATETIME", "DATETIME"),
    TIMESTAMP("TIMESTAMP", "TIMESTAMP"),
    TIME("TIME", "TIME"),
    FLOAT("FLOAT", "FLOAT"),
    BINARY("BINARY", "BINARY"),
    VARBINARY("VARBINARY", "VARBINARY"),
    JSON("JSON", "JSON"),
    ARRAY("ARRAY", "ARRAY"),
    DOUBLE("DOUBLE", "DOUBLE"),
    MAP("MAP", "MAP"),
    STRUCT("STRUCT", "STRUCT");

    @EnumValue
    private final String code;
    private final String label;
}