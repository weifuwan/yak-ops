package io.yak.ops.business.datasource;

import io.yak.ops.common.bean.dto.datasource.DataSourceCatalogQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourcePreviewDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePageDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePreviewVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceTablePageVO;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.util.List;

/**
 * Provides access to physical database metadata through datasources in the active Workspace.
 *
 * <p>Metadata queries use the saved datasource connection without exposing its credentials to callers.
 */
public interface DataSourceCatalogService {

    /**
     * Lists databases accessible through the specified datasource.
     *
     * @param datasourceId identifier of the datasource in the active Workspace
     * @return names of accessible databases
     */
    List<String> listDatabases(String datasourceId);

    /**
     * Lists schemas accessible within the specified database.
     *
     * @param datasourceId identifier of the datasource in the active Workspace
     * @param database database to query, or {@code null} if unspecified
     * @return names of accessible schemas
     */
    List<String> listSchemas(String datasourceId, String database);

    /**
     * Lists tables and views matching the requested metadata filters.
     *
     * @param datasourceId identifier of the datasource in the active Workspace
     * @param query database, schema, name filter and result limit
     * @return metadata for matching tables and views
     */
    List<DataSourceCatalogTableVO> listTables(String datasourceId, DataSourceCatalogQueryDTO query);

    /**
     * Searches complete Workspace-authorized metadata and returns a filtered page.
     *
     * @param datasourceId datasource in the active Workspace
     * @param query exact namespace, patterns and continuation offset
     * @return matching tables and an explicit next offset, without credentials
     */
    DataSourceTablePageVO listTablePage(String datasourceId, DataSourceTablePageDTO query);

    /**
     * Previews an unsaved source read without creating or changing any Task state.
     *
     * @param datasourceId datasource in the active Workspace
     * @param query physical table, source predicate and bounded result count
     * @return precision-preserving data and truncation information
     */
    DataSourcePreviewVO preview(String datasourceId, DataSourcePreviewDTO query);

    /**
     * Lists column metadata for a physical table.
     *
     * @param datasourceId identifier of the datasource in the active Workspace
     * @param path database, schema and table name identifying the table
     * @return column metadata, including native types and primary-key information
     */
    List<DataSourceCatalogColumnVO> listColumns(String datasourceId, DataSourceTablePathDTO path);

    /**
     * Retrieves the dialect-normalized schema of a physical table.
     *
     * @param datasourceId identifier of the datasource in the active Workspace
     * @param tableId physical table identifier containing catalog, schema and table name
     * @return logical column types and primary-key metadata for the table
     */
    TableSchema getTableSchema(String datasourceId, TableId tableId);
}
