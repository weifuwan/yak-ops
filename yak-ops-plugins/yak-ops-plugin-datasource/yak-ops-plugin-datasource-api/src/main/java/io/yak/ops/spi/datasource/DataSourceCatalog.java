package io.yak.ops.spi.datasource;

import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogReadRequest;
import io.yak.ops.spi.datasource.catalog.DataSourceTablePath;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import io.yak.ops.spi.datasource.query.DataSourceQueryResult;
import java.util.List;

/** Typed datasource Catalog metadata and lightweight-read contract. */
public interface DataSourceCatalog {

    List<String> listDatabases();

    List<String> listSchemas(String database);

    List<DataSourceTable> listTables(DataSourceCatalogQuery query);

    List<DataSourceColumn> listColumns(DataSourceTablePath tablePath);

    /** Resolve columns for a typed TABLE or SQL read request. */
    List<DataSourceColumn> describe(DataSourceCatalogReadRequest request);

    /** Preview data with a plugin-enforced maximum row limit. */
    DataSourceQueryResult preview(DataSourceCatalogReadRequest request, int limit);

    /** Count rows for a typed TABLE or SQL read request. */
    long count(DataSourceCatalogReadRequest request);

    /** Build a SELECT template from one logical table path. */
    String buildSqlTemplate(String tablePath);

    /** Resolve plugin-supported SQL variables using the typed request context. */
    String resolveSql(String sql, DataSourceCatalogReadRequest request);
}
