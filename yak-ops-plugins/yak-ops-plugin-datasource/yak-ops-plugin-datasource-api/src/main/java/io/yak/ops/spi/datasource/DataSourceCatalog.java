package io.yak.ops.spi.datasource;

import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.catalog.DataSourceTablePath;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.util.List;

/**
 * 数据源 Catalog 元数据契约，只负责数据库、Schema、表和字段发现。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface DataSourceCatalog {

    List<String> listDatabases();

    List<String> listSchemas(String database);

    List<DataSourceTable> listTables(DataSourceCatalogQuery query);

    List<DataSourceColumn> listColumns(DataSourceTablePath tablePath);
}
