package io.yak.ops.modeling.ddl.model;

/** Immutable database-neutral physical column definition. */
public class DdlColumnDefinition {

    private final String name;
    private final DdlDataType dataType;
    private final Integer length;
    private final Integer precision;
    private final Integer scale;
    private final Integer datetimePrecision;
    private final Boolean nullable;
    private final boolean primaryKey;
    private final String defaultValue;
    private final String comment;

    public DdlColumnDefinition(
            String name,
            DdlDataType dataType,
            Integer length,
            Integer precision,
            Integer scale,
            Integer datetimePrecision,
            Boolean nullable,
            boolean primaryKey,
            String defaultValue,
            String comment) {
        this.name = name;
        this.dataType = dataType;
        this.length = length;
        this.precision = precision;
        this.scale = scale;
        this.datetimePrecision = datetimePrecision;
        this.nullable = nullable;
        this.primaryKey = primaryKey;
        this.defaultValue = defaultValue;
        this.comment = comment;
    }

    public String getName() { return name; }
    public DdlDataType getDataType() { return dataType; }
    public Integer getLength() { return length; }
    public Integer getPrecision() { return precision; }
    public Integer getScale() { return scale; }
    public Integer getDatetimePrecision() { return datetimePrecision; }
    public Boolean getNullable() { return nullable; }
    public boolean isPrimaryKey() { return primaryKey; }
    public String getDefaultValue() { return defaultValue; }
    public String getComment() { return comment; }
}
