package io.yak.ops.spi.datasource.catalog;

/** Catalog 中表的完整定位信息。 */
public final class DataSourceTablePath {

    private final String database;
    private final String schema;
    private final String table;

    public DataSourceTablePath(String database, String schema, String table) {
        if (table == null || table.trim().isEmpty()) {
            throw new IllegalArgumentException("table 不能为空");
        }
        this.database = database;
        this.schema = schema;
        this.table = table.trim();
    }

    public String getDatabase() {
        return database;
    }

    public String getSchema() {
        return schema;
    }

    public String getTable() {
        return table;
    }
}
