package io.yak.ops.spi.datasource.catalog;

/**
 * Catalog 表元数据查询条件。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public final class DataSourceCatalogQuery {

    /** 数据库名称。 */
    private final String database;

    /** Schema 名称。 */
    private final String schema;

    /** 表名搜索关键字。 */
    private final String keyword;

    /** 最大返回数量。 */
    private final Integer limit;

    public DataSourceCatalogQuery(String database, String schema, String keyword) {
        this(database, schema, keyword, null);
    }

    public DataSourceCatalogQuery(String database, String schema, String keyword, Integer limit) {
        this.database = database;
        this.schema = schema;
        this.keyword = keyword;
        this.limit = limit == null ? null : Math.max(1, limit);
    }

    public String getDatabase() {
        return database;
    }

    public String getSchema() {
        return schema;
    }

    public String getKeyword() {
        return keyword;
    }

    public Integer getLimit() {
        return limit;
    }
}
