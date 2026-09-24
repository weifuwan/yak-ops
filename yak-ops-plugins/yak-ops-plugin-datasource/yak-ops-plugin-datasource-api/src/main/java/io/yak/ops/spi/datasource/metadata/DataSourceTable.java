package io.yak.ops.spi.datasource.metadata;

/** 数据库表或视图元数据。 */
public final class DataSourceTable {

    private final String database;
    private final String schema;
    private final String name;
    private final String type;
    private final String remarks;

    public DataSourceTable(String database, String schema, String name, String type, String remarks) {
        this.database = database;
        this.schema = schema;
        this.name = name;
        this.type = type;
        this.remarks = remarks;
    }

    public String getDatabase() {
        return database;
    }

    public String getSchema() {
        return schema;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getRemarks() {
        return remarks;
    }
}
