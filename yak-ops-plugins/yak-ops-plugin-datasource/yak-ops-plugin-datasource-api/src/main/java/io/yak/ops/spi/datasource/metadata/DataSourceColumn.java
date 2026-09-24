package io.yak.ops.spi.datasource.metadata;

/**
 * 数据源字段元数据。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public final class DataSourceColumn {

    /** 字段名称。 */
    private final String name;

    /** 数据源原生类型名称。 */
    private final String typeName;

    /** JDBC Types 对应的类型编码。 */
    private final int jdbcType;

    /** 字段长度或精度。 */
    private final Integer size;

    /** 小数位数。 */
    private final Integer scale;

    /** 是否允许为空。 */
    private final boolean nullable;

    /** 字段顺序。 */
    private final int ordinalPosition;

    /** 是否为主键字段。 */
    private final boolean primaryKey;

    /** 数据源字段备注。 */
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
