package io.yak.ops.spi.datasource.catalog;

/**
 * Catalog 中表的完整定位信息。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public final class DataSourceTablePath {

    /** 数据库名称。 */
    private final String database;

    /** Schema 名称。 */
    private final String schema;

    /** 表、视图、Collection 或 Index 名称。 */
    private final String table;

    public DataSourceTablePath(String database, String schema, String table) {
        if (table == null || table.trim().isEmpty()) throw new IllegalArgumentException("table 不能为空");
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
