package io.yak.ops.plugin.datasource.api.catalog;

import java.util.List;

/**
 * 数据源 Catalog 元数据契约，只负责数据库、Schema、表和字段发现。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface DataSourceCatalog {

    /** @return 当前连接可见的数据库列表 */
    List<String> listDatabases();

    /**
     * 查询指定数据库下可见的 Schema。
     *
     * @param database 数据库名称
     */
    List<String> listSchemas(String database);

    /**
     * 按数据库、Schema、关键字等条件查询表或视图。
     *
     * @param query Catalog 查询条件
     */
    List<DataSourceTable> listTables(DataSourceCatalogQuery query);

    /**
     * 查询指定表的字段元数据。
     *
     * @param tablePath 表完整定位信息
     */
    List<DataSourceColumn> listColumns(DataSourceTablePath tablePath);
}
