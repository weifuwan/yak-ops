package io.yak.ops.spi.datasource.metadata;

/**
 * 数据源表、视图、Collection 或 Index 元数据。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public final class DataSourceTable {

    /** 所属数据库名称。 */
    private final String database;

    /** 所属 Schema 名称；无 Schema 概念的数据源为空。 */
    private final String schema;

    /** 元数据对象名称。 */
    private final String name;

    /** 元数据类型，例如 TABLE、VIEW、COLLECTION、INDEX。 */
    private final String type;

    /** 数据源对象备注。 */
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
