package io.yak.ops.plugin.datasource.api.catalog;

public class DataSourceCatalogColumn {

    private final String name;
    private final String typeName;
    private final Integer jdbcType;
    private final Integer size;
    private final Integer scale;
    private final Boolean nullable;
    private final Integer ordinalPosition;
    private final boolean primaryKey;
    private final String remarks;

    public DataSourceCatalogColumn(
            String name,
            String typeName,
            Integer jdbcType,
            Integer size,
            Integer scale,
            Boolean nullable,
            Integer ordinalPosition,
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

    public String getName() { return name; }
    public String getTypeName() { return typeName; }
    public Integer getJdbcType() { return jdbcType; }
    public Integer getSize() { return size; }
    public Integer getScale() { return scale; }
    public Boolean getNullable() { return nullable; }
    public Integer getOrdinalPosition() { return ordinalPosition; }
    public boolean isPrimaryKey() { return primaryKey; }
    public String getRemarks() { return remarks; }
}
