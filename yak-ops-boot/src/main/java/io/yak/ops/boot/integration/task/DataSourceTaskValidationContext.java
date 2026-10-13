package io.yak.ops.boot.integration.task;

import io.yak.ops.business.datasource.DataSourceCatalogService;
import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.validation.DataSourceInfo;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Binds plugin validation to the caller's authorized Datasource and physical metadata.
 *
 * <p>Catalog access uses one lookup for each table. Credentials and connections are never
 * passed to Task Plugins, and metadata failures are not swallowed as missing tables.
 */
@Component
public class DataSourceTaskValidationContext implements TaskValidationContext {
    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private DataSourceCatalogService catalogService;

    @Override
    public DataSourceInfo dataSource(String id) {
        DataSourceVO value = dataSourceService.queryDataSource(id);
        return new DataSourceInfo(value.getDbType(), value.getDatabase(), value.getSchema());
    }

    @Override
    public TableSchema tableSchema(String datasourceId, TableId tableId) {
        return catalogService.getTableSchema(datasourceId, tableId);
    }
}
