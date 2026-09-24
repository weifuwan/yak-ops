package io.yak.ops.business.datasource.gateway.adapter;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogQueryResult;
import io.yak.ops.business.datasource.domain.catalog.CatalogQueryResult.QueryColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.datasource.domain.catalog.CatalogTablePath;
import io.yak.ops.business.datasource.domain.catalog.CatalogTableQuery;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogReadRequest;
import io.yak.ops.spi.datasource.catalog.DataSourceTablePath;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import io.yak.ops.spi.datasource.query.DataSourceQueryColumn;
import io.yak.ops.spi.datasource.query.DataSourceQueryResult;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Datasource Catalog SPI -> typed Business Catalog Gateway Adapter. */
@Component
@ConditionalOnDataSourceEnabled
public class SpiDataSourceCatalogGateway implements DataSourceCatalogGateway {

    @Resource
    private DataSourcePluginRegistry pluginRegistry;

    @Resource
    private DataSourceProperties properties;

    @Override
    public List<String> listDatabases(DataSourceDefinition dataSource, int timeoutSeconds) {
        return execute(
                dataSource, timeoutSeconds, DataSourceCapability.CATALOG_METADATA, DataSourceCatalog::listDatabases);
    }

    @Override
    public List<String> listSchemas(DataSourceDefinition dataSource, String database, int timeoutSeconds) {
        return execute(
                dataSource,
                timeoutSeconds,
                DataSourceCapability.CATALOG_METADATA,
                catalog -> catalog.listSchemas(database));
    }

    @Override
    public List<CatalogTable> listTables(DataSourceDefinition dataSource, CatalogTableQuery query, int timeoutSeconds) {
        CatalogTableQuery value = query == null ? new CatalogTableQuery(null, null, null) : query;
        return execute(
                dataSource,
                timeoutSeconds,
                DataSourceCapability.CATALOG_METADATA,
                catalog -> catalog
                        .listTables(new DataSourceCatalogQuery(
                                value.database(), value.schema(), value.keyword(), value.limit()))
                        .stream()
                        .map(this::toTable)
                        .toList());
    }

    @Override
    public List<CatalogColumn> listColumns(
            DataSourceDefinition dataSource, CatalogTablePath tablePath, int timeoutSeconds) {
        if (tablePath == null) {
            throw new DataSourceException(DataSourceErrorCode.CATALOG_FAILED, "Catalog 表路径不能为空");
        }
        return execute(
                dataSource,
                timeoutSeconds,
                DataSourceCapability.CATALOG_METADATA,
                catalog -> catalog
                        .listColumns(
                                new DataSourceTablePath(tablePath.database(), tablePath.schema(), tablePath.table()))
                        .stream()
                        .map(this::toColumn)
                        .toList());
    }

    @Override
    public List<CatalogColumn> describe(
            DataSourceDefinition dataSource, CatalogReadRequest request, int timeoutSeconds) {
        DataSourceCatalogReadRequest pluginRequest = toPluginRequest(request);
        return execute(
                dataSource,
                timeoutSeconds,
                DataSourceCapability.CATALOG_READ,
                catalog -> catalog.describe(pluginRequest).stream()
                        .map(this::toColumn)
                        .toList());
    }

    @Override
    public CatalogQueryResult preview(
            DataSourceDefinition dataSource, CatalogReadRequest request, int limit, int timeoutSeconds) {
        DataSourceCatalogReadRequest pluginRequest = toPluginRequest(request);
        return execute(
                dataSource,
                timeoutSeconds,
                DataSourceCapability.CATALOG_READ,
                catalog -> toQueryResult(catalog.preview(pluginRequest, limit)));
    }

    @Override
    public long count(DataSourceDefinition dataSource, CatalogReadRequest request, int timeoutSeconds) {
        DataSourceCatalogReadRequest pluginRequest = toPluginRequest(request);
        return execute(
                dataSource, timeoutSeconds, DataSourceCapability.CATALOG_READ, catalog -> catalog.count(pluginRequest));
    }

    @Override
    public String buildSqlTemplate(DataSourceDefinition dataSource, String tablePath, int timeoutSeconds) {
        return execute(
                dataSource,
                timeoutSeconds,
                DataSourceCapability.CATALOG_READ,
                catalog -> catalog.buildSqlTemplate(tablePath));
    }

    @Override
    public String resolveSql(DataSourceDefinition dataSource, CatalogReadRequest request, int timeoutSeconds) {
        if (request == null || request.sql() == null) {
            throw new DataSourceException(DataSourceErrorCode.CATALOG_FAILED, "解析 SQL 时 query 不能为空");
        }
        DataSourceCatalogReadRequest pluginRequest = toPluginRequest(request);
        return execute(
                dataSource,
                timeoutSeconds,
                DataSourceCapability.CATALOG_READ,
                catalog -> catalog.resolveSql(request.sql(), pluginRequest));
    }

    private <T> T execute(
            DataSourceDefinition dataSource,
            int connectionTimeoutSeconds,
            DataSourceCapability capability,
            Function<DataSourceCatalog, T> action) {
        if (dataSource == null || dataSource.getDbType() == null) {
            throw new DataSourceException(DataSourceErrorCode.CATALOG_FAILED, "数据源定义或类型不能为空");
        }
        try {
            DataSourcePlugin plugin = pluginRegistry.get(dataSource.getDbType());
            if (!plugin.supports(capability)) {
                throw new DataSourceException(
                        DataSourceErrorCode.CATALOG_FAILED,
                        "数据源插件未声明能力 " + capability.name() + "："
                                + plugin.dbType().name());
            }
            DataSourceConnection connection = plugin.parseConnection(dataSource.getConnectionParams());
            DataSourceCatalog catalog =
                    plugin.createCatalog(connection, Math.max(1, connectionTimeoutSeconds), queryTimeoutSeconds());
            return action.apply(catalog);
        } catch (DataSourceException exception) {
            throw exception;
        } catch (DataSourcePluginException exception) {
            throw catalogException(exception);
        } catch (RuntimeException exception) {
            throw catalogException(exception);
        }
    }

    private int queryTimeoutSeconds() {
        return Math.max(1, properties.getCatalog().getQueryTimeoutSeconds());
    }

    private DataSourceCatalogReadRequest toPluginRequest(CatalogReadRequest request) {
        if (request == null) {
            throw new DataSourceException(DataSourceErrorCode.CATALOG_FAILED, "Catalog 请求不能为空");
        }
        Map<String, String> variables = new LinkedHashMap<>();
        for (CatalogReadRequest.Variable variable : request.variables()) {
            // Historic plugin behavior ignored paramsList entries with null values. Preserve that
            // compatibility while removing the implicit Map protocol from the stable SPI.
            if (variable.value() != null) {
                variables.put(variable.name(), variable.value());
            }
        }
        return new DataSourceCatalogReadRequest(
                request.sqlMode() ? DataSourceCatalogReadRequest.Mode.SQL : DataSourceCatalogReadRequest.Mode.TABLE,
                request.tablePath(),
                request.sql(),
                variables);
    }

    private CatalogTable toTable(DataSourceTable table) {
        return new CatalogTable(
                table.getDatabase(), table.getSchema(), table.getName(), table.getType(), table.getRemarks());
    }

    private CatalogColumn toColumn(DataSourceColumn column) {
        return new CatalogColumn(
                column.getName(),
                column.getTypeName(),
                column.getJdbcType(),
                column.getSize(),
                column.getScale(),
                column.isNullable(),
                column.getOrdinalPosition(),
                column.isPrimaryKey(),
                column.getRemarks());
    }

    private CatalogQueryResult toQueryResult(DataSourceQueryResult result) {
        if (result == null) return new CatalogQueryResult(List.of(), List.of(), 0L);
        List<QueryColumn> columns =
                result.getColumns().stream().map(this::toQueryColumn).toList();
        return new CatalogQueryResult(columns, result.getData(), result.getTotal());
    }

    private QueryColumn toQueryColumn(DataSourceQueryColumn column) {
        return new QueryColumn(column.getTitle(), column.getDataIndex(), column.getKey(), column.isEllipsis());
    }

    private DataSourceException catalogException(RuntimeException exception) {
        return new DataSourceException(DataSourceErrorCode.CATALOG_FAILED, exception.getMessage(), exception);
    }
}
