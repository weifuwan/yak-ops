package io.yak.ops.business.datasource.gateway;

import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogQueryResult;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.datasource.domain.catalog.CatalogTablePath;
import io.yak.ops.business.datasource.domain.catalog.CatalogTableQuery;
import java.util.List;

/**
 * Business Datasource 对 Catalog 物理能力的 Port。
 *
 * <p>Gateway 只接受 Business-owned typed catalog models；Plugin SPI 的 Map 协议和 metadata model 必须在 Adapter 内完成转换。
 */
public interface DataSourceCatalogGateway {

    List<String> listDatabases(DataSourceDefinition dataSource, int timeoutSeconds);

    List<String> listSchemas(DataSourceDefinition dataSource, String database, int timeoutSeconds);

    List<CatalogTable> listTables(DataSourceDefinition dataSource, CatalogTableQuery query, int timeoutSeconds);

    List<CatalogColumn> listColumns(DataSourceDefinition dataSource, CatalogTablePath tablePath, int timeoutSeconds);

    List<CatalogColumn> describe(DataSourceDefinition dataSource, CatalogReadRequest request, int timeoutSeconds);

    CatalogQueryResult preview(
            DataSourceDefinition dataSource, CatalogReadRequest request, int limit, int timeoutSeconds);

    long count(DataSourceDefinition dataSource, CatalogReadRequest request, int timeoutSeconds);

    String buildSqlTemplate(DataSourceDefinition dataSource, String tablePath, int timeoutSeconds);

    String resolveSql(DataSourceDefinition dataSource, CatalogReadRequest request, int timeoutSeconds);
}
