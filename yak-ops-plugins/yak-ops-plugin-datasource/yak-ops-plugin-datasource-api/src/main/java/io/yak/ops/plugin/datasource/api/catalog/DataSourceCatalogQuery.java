package io.yak.ops.plugin.datasource.api.catalog;

/**
 * Catalog 表元数据查询条件。
 *
 * @param database 数据库名称
 * @param schema Schema 名称
 * @param keyword 表名搜索关键字
 * @param limit 最大返回数量；为空表示不限制
 * @author weifuwan
 * @since 2026-09-24
 */
public record DataSourceCatalogQuery(String database, String schema, String keyword, Integer limit) {

    public DataSourceCatalogQuery(String database, String schema, String keyword) {
        this(database, schema, keyword, null);
    }

    public DataSourceCatalogQuery {
        if (limit != null && limit < 1) throw new IllegalArgumentException("limit 必须大于 0");
    }
}
