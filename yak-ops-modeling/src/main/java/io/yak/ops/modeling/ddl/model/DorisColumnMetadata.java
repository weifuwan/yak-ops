package io.yak.ops.modeling.ddl.model;

/** Normalized column metadata read through the datasource catalog SPI. */
public class DorisColumnMetadata {

    private final String name;
    private final String typeName;
    private final Integer size;
    private final Integer scale;
    private final Boolean nullable;
    private final boolean primaryKey;

    public DorisColumnMetadata(
            String name, String typeName, Integer size, Integer scale, Boolean nullable, boolean primaryKey) {
        this.name = name;
        this.typeName = typeName;
        this.size = size;
        this.scale = scale;
        this.nullable = nullable;
        this.primaryKey = primaryKey;
    }

    public String getName() { return name; }
    public String getTypeName() { return typeName; }
    public Integer getSize() { return size; }
    public Integer getScale() { return scale; }
    public Boolean getNullable() { return nullable; }
    public boolean isPrimaryKey() { return primaryKey; }
}
