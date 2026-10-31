package io.yak.ops.business.datasource.impl;

import io.yak.ops.business.datasource.DataSourceCatalogService;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.bean.dto.datasource.DataSourceCatalogQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourcePreviewDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePageDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePreviewVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceTablePageVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.JdbcColumnInfo;
import io.yak.ops.connector.jdbc.database.catalog.JdbcDataPreview;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTableInfo;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTablePage;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.query.JdbcReadQuery;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import jakarta.annotation.Resource;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Resolves authorized datasource connections and executes metadata queries through JDBC Catalog.
 *
 * <p>Each query owns one Catalog lifecycle. The shared JDBC provider creates physical
 * connections and releases associated SSH resources when the caller closes them.
 */
@Service
public class DataSourceCatalogServiceImpl implements DataSourceCatalogService {

    @Resource
    private DataSourceLookupService resources;

    @Override
    public List<String> listDatabases(String datasourceId) {
        return query(datasourceId, JdbcCatalog::listDatabases);
    }

    @Override
    public List<String> listSchemas(String datasourceId, String database) {
        return query(datasourceId, catalog -> catalog.listSchemas(StringUtils.trimToNull(database)));
    }

    @Override
    public List<DataSourceCatalogTableVO> listTables(String datasourceId, DataSourceCatalogQueryDTO parameters) {
        List<JdbcTableInfo> tables = query(
                datasourceId,
                catalog -> catalog.listTableInfos(
                        StringUtils.trimToNull(parameters.getDatabase()),
                        StringUtils.trimToNull(parameters.getSchema()),
                        StringUtils.trimToNull(parameters.getKeyword()),
                        parameters.getLimit()));
        return tables.stream().map(DataSourceCatalogServiceImpl::tableView).toList();
    }

    @Override
    public DataSourceTablePageVO listTablePage(String datasourceId, DataSourceTablePageDTO parameters) {
        DataSourceInfo authorized = resources.dataSource(WorkspaceContext.requireWorkspaceId(), datasourceId);
        TableId namespace;
        try {
            // Only the namespace is queried; the sentinel never identifies a physical table.
            namespace = authorized.resolveTable(parameters.getDatabase(), parameters.getSchema(), "_catalog_scope");
            if (StringUtils.trimToNull(parameters.getDatabasePattern()) != null) {
                Pattern.compile(parameters.getDatabasePattern());
            }
            if (StringUtils.trimToNull(parameters.getTablePattern()) != null) {
                Pattern.compile(parameters.getTablePattern());
            }
        } catch (IllegalArgumentException failure) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CATALOG_FILTER, null, failure);
        }
        JdbcTablePage page = query(
                datasourceId,
                authorized.revision(),
                DataSourceErrorCode.CATALOG_QUERY_FAILED,
                catalog -> catalog.listTablePage(
                        StringUtils.trimToNull(namespace.catalog()),
                        StringUtils.trimToNull(namespace.schema()),
                        StringUtils.trimToNull(parameters.getKeyword()),
                        StringUtils.trimToNull(parameters.getDatabasePattern()),
                        StringUtils.trimToNull(parameters.getTablePattern()),
                        parameters.getOffset(),
                        parameters.getLimit() == null ? 200 : parameters.getLimit()));
        return new DataSourceTablePageVO(
                page.items().stream()
                        .map(DataSourceCatalogServiceImpl::tableView)
                        .toList(),
                page.nextOffset());
    }

    @Override
    public DataSourcePreviewVO preview(String datasourceId, DataSourcePreviewDTO parameters) {
        DataSourceInfo authorized = resources.dataSource(WorkspaceContext.requireWorkspaceId(), datasourceId);
        String predicate;
        TableId tableId;
        try {
            predicate = JdbcReadQuery.validateWhere(parameters.getWhere());
            tableId = authorized.resolveTable(
                    parameters.getDatabase(),
                    parameters.getSchema(),
                    parameters.getTable().trim());
        } catch (IllegalArgumentException failure) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_PREVIEW_CONFIGURATION, null, failure);
        }
        JdbcDataPreview preview = query(
                datasourceId,
                authorized.revision(),
                DataSourceErrorCode.PREVIEW_QUERY_FAILED,
                catalog -> catalog.preview(tableId, predicate, parameters.getLimit()));
        return new DataSourcePreviewVO(
                preview.columns().stream()
                        .map(DataSourceCatalogServiceImpl::columnView)
                        .toList(),
                preview.rows(),
                preview.truncated());
    }

    @Override
    public List<DataSourceCatalogColumnVO> listColumns(String datasourceId, DataSourceTablePathDTO path) {
        TableId tableId = new TableId(
                StringUtils.trimToNull(path.getDatabase()),
                StringUtils.trimToNull(path.getSchema()),
                path.getTable().trim());
        List<JdbcColumnInfo> columns = query(datasourceId, catalog -> catalog.getColumns(tableId));
        return columns.stream().map(DataSourceCatalogServiceImpl::columnView).toList();
    }

    @Override
    public TableSchema getTableSchema(String datasourceId, TableId tableId) {
        return query(datasourceId, catalog -> catalog.getTable(tableId));
    }

    private <T> T query(String datasourceId, CatalogAction<T> action) {
        return query(datasourceId, DataSourceErrorCode.CATALOG_QUERY_FAILED, action);
    }

    private <T> T query(String datasourceId, DataSourceErrorCode error, CatalogAction<T> action) {
        JdbcConnectionOptions binding = new JdbcConnectionOptions(
                resources.connectionOptions(WorkspaceContext.requireWorkspaceId(), datasourceId));
        return query(binding, error, action);
    }

    private <T> T query(String datasourceId, long revision, DataSourceErrorCode error, CatalogAction<T> action) {
        JdbcConnectionOptions binding = new JdbcConnectionOptions(
                resources.connectionOptions(WorkspaceContext.requireWorkspaceId(), datasourceId, revision));
        return query(binding, error, action);
    }

    private <T> T query(JdbcConnectionOptions binding, DataSourceErrorCode error, CatalogAction<T> action) {
        try (JdbcCatalog catalog =
                JdbcCatalogFactory.create(binding.url(), new DefaultJdbcConnectionProvider(binding))) {
            return action.execute(catalog);
        } catch (SQLException | RuntimeException exception) {
            throw new DataSourceException(error, null, exception);
        }
    }

    private static DataSourceCatalogTableVO tableView(JdbcTableInfo table) {
        DataSourceCatalogTableVO result = new DataSourceCatalogTableVO();
        result.setDatabase(table.tableId().catalog());
        result.setSchema(table.tableId().schema());
        result.setName(table.tableId().table());
        result.setType(table.type());
        result.setRemarks(table.remarks());
        return result;
    }

    private static DataSourceCatalogColumnVO columnView(JdbcColumnInfo column) {
        DataSourceCatalogColumnVO result = new DataSourceCatalogColumnVO();
        result.setName(column.name());
        result.setTypeName(column.typeName());
        result.setJdbcType(column.jdbcType());
        result.setSize(column.size());
        result.setScale(column.scale());
        result.setNullable(column.nullable());
        result.setOrdinalPosition(column.ordinalPosition());
        result.setPrimaryKey(column.primaryKey());
        result.setPrimaryKeyPosition(column.primaryKeyPosition());
        result.setRemarks(column.remarks());
        return result;
    }

    @FunctionalInterface
    private interface CatalogAction<T> {
        T execute(JdbcCatalog catalog) throws SQLException;
    }
}
