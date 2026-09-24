package io.yak.ops.spi.datasource.metadata;

/** 数据库字段元数据。 */
public final class DataSourceColumn {

    private final String name;
    private final String typeName;
    private final int jdbcType;
    private final Integer size;
    private final Integer scale;
    private final boolean nullable;
    private final int ordinalPosition;
    private final boolean primaryKey;
    private final String remarks;

    public DataSourceColumn(
            String name,
            String typeName,
            int jdbcType,
            Integer size,
            Integer scale,
            boolean nullable,
            int ordinalPosition,
            boolean primaryKey,
            String remarks) {
        this.name = name;
        this.typeName = typeName;
        this.jdbcType = jdbcType;
        this.size = size;
        this.scale = scale;
        this.nullable = nullable;
        this.ordinalPosition = ordinalPosition;
        this.primaryKey = primaryKey;
        this.remarks = remarks;
    }

    public String getName() {
        return name;
    }

    public String getTypeName() {
        return typeName;
    }

    public int getJdbcType() {
        return jdbcType;
    }

    public Integer getSize() {
        return size;
    }

    public Integer getScale() {
        return scale;
    }

    public boolean isNullable() {
        return nullable;
    }

    public int getOrdinalPosition() {
        return ordinalPosition;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public String getRemarks() {
        return remarks;
    }
}
