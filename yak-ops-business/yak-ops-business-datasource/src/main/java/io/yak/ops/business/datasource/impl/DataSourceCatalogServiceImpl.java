package io.yak.ops.business.datasource.impl;

import io.yak.ops.business.datasource.DataSourceCatalogService;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.runtime.DataSourceRuntimeConnection;
import io.yak.ops.business.datasource.runtime.DataSourceRuntimeService;
import io.yak.ops.common.bean.dto.datasource.DataSourceCatalogQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import jakarta.annotation.Resource;
import java.sql.SQLException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Resolves authorized datasource connections and executes metadata queries through JDBC Catalog.
 *
 * <p>Each query owns one Catalog lifecycle. Datasource plugins create and close their physical
 * connections, including the SSH resources associated with those connections.
 */
@Service
public class DataSourceCatalogServiceImpl implements DataSourceCatalogService {

    @Resource
    private DataSourceRuntimeService resources;

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
        var tables = query(
                datasourceId,
                catalog -> catalog.listTableInfos(
                        StringUtils.trimToNull(parameters.getDatabase()),
                        StringUtils.trimToNull(parameters.getSchema()),
                        StringUtils.trimToNull(parameters.getKeyword()),
                        parameters.getLimit()));
        return tables.stream()
                .map(table -> {
                    DataSourceCatalogTableVO result = new DataSourceCatalogTableVO();
                    result.setDatabase(table.tableId().catalog());
                    result.setSchema(table.tableId().schema());
                    result.setName(table.tableId().table());
                    result.setType(table.type());
                    result.setRemarks(table.remarks());
                    return result;
                })
                .toList();
    }

    @Override
    public List<DataSourceCatalogColumnVO> listColumns(String datasourceId, DataSourceTablePathDTO path) {
        TableId tableId = new TableId(
                StringUtils.trimToNull(path.getDatabase()),
                StringUtils.trimToNull(path.getSchema()),
                path.getTable().trim());
        var columns = query(datasourceId, catalog -> catalog.getColumns(tableId));
        return columns.stream()
                .map(column -> {
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
                })
                .toList();
    }

    @Override
    public TableSchema getTableSchema(String datasourceId, TableId tableId) {
        return query(datasourceId, catalog -> catalog.getTable(tableId));
    }

    private <T> T query(String datasourceId, CatalogAction<T> action) {
        DataSourceRuntimeConnection binding = resources.connection(WorkspaceContext.requireWorkspaceId(), datasourceId);
        try (JdbcCatalog catalog = JdbcCatalogFactory.create(binding.jdbcUrl(), binding.provider())) {
            return action.execute(catalog);
        } catch (SQLException | RuntimeException exception) {
            throw new DataSourceException(DataSourceErrorCode.CATALOG_QUERY_FAILED, null, exception);
        }
    }

    @FunctionalInterface
    private interface CatalogAction<T> {
        T execute(JdbcCatalog catalog) throws SQLException;
    }
}
