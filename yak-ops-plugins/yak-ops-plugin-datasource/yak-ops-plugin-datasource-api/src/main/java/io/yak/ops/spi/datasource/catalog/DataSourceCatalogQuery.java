package io.yak.ops.spi.datasource.catalog;

/** Catalog 表查询条件。 */
public final class DataSourceCatalogQuery {

    private final String database;
    private final String schema;
    private final String keyword;
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
