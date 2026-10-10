package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTableRouteDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.connector.jdbc.database.JdbcSchemaCompatibility;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncTableRouteRepository;
import jakarta.annotation.Resource;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DataSyncTaskDefinitionValidator 的业务职责实现。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Component
public class DataSyncTaskDefinitionValidator {

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private DataSyncTableRouteRepository tableRouteRepository;

    /**
     * 拒绝历史多表任务被静默投影为首张表。旧 Route 数据只读，不再参与新任务双写。
     */
    public void requireSingleTableTask(DataSyncTaskEntity task) {
        if (task == null || task.getId() == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK);
        }
        if (tableRouteRepository.queryByTask(task.getWorkspaceId(), task.getId()).size() > 1) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "历史多表任务暂不支持编辑或运行");
        }
    }

    public void rejectMultiRouteRequest(DataSyncTaskDTO dto) {
        if (dto.getTableRoutes() != null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "多表同步暂不可用，请使用单表任务配置");
        }
    }

    public void validatePersistedTaskDefinition(DataSyncTaskEntity task) {
        requireSingleTableTask(task);
        DataSyncWriteMode mode = task.getWriteMode() == null ? DataSyncWriteMode.APPEND : task.getWriteMode();
        validateWriteMode(task.getSyncType(), mode);
        rejectLegacyPolicies(task.getAutoCreateTable(), task.getMappingConfig());
        DataSyncTableRouteDTO route = new DataSyncTableRouteDTO();
        route.setSourceDatabase(task.getSourceDatabase());
        route.setSourceSchema(task.getSourceSchema());
        route.setSourceTable(task.getSourceTable());
        route.setTargetDatabase(task.getTargetDatabase());
        route.setTargetSchema(task.getTargetSchema());
        route.setTargetTable(task.getTargetTable());
        DataSyncTableRouteDTO resolved =
                resolveRouteScope(task.getSourceDataSourceId(), task.getTargetDataSourceId(), route);
        if (task.getSyncType() == DataSyncType.REALTIME) {
            validateRealtimeDatasourceTypes(task.getSourceDataSourceId(), task.getTargetDataSourceId());
        }
        validateRouteTables(task.getSourceDataSourceId(), task.getTargetDataSourceId(), resolved, task.getSyncType(), mode);
    }

    private void rejectLegacyPolicies(Boolean autoCreate, String mappingJson) {
        if (Boolean.TRUE.equals(autoCreate) || StringUtils.isNotBlank(mappingJson)) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "任务包含已下线的字段映射或自动建表配置，请重新编辑并保存后再上线");
        }
    }

    public DataSyncTableRouteDTO resolveTaskScope(DataSyncTaskDTO task) {
        DataSyncTableRouteDTO route = new DataSyncTableRouteDTO();
        route.setSourceDatabase(task.getSourceDatabase());
        route.setSourceSchema(task.getSourceSchema());
        route.setSourceTable(task.getSourceTable());
        route.setTargetDatabase(task.getTargetDatabase());
        route.setTargetSchema(task.getTargetSchema());
        route.setTargetTable(task.getTargetTable());
        return resolveRouteScope(task.getSourceDataSourceId(), task.getTargetDataSourceId(), route);
    }

    private DataSyncTableRouteDTO resolveRouteScope(String sourceId, String targetId, DataSyncTableRouteDTO value) {
        DataSourceVO source = dataSourceService.queryDataSource(sourceId);
        DataSourceVO target = dataSourceService.queryDataSource(targetId);
        DataSyncTableRouteDTO resolved = BeanCopyUtils.copy(value, DataSyncTableRouteDTO.class);
        resolved.setSourceDatabase(scopeValue(source.getDatabase(), value.getSourceDatabase()));
        resolved.setSourceSchema(scopeValue(source.getSchema(), value.getSourceSchema()));
        resolved.setTargetDatabase(scopeValue(target.getDatabase(), value.getTargetDatabase()));
        resolved.setTargetSchema(scopeValue(target.getSchema(), value.getTargetSchema()));
        return resolved;
    }

    public void validateTaskDefinition(
            DataSyncType syncType, DataSyncTaskDTO dto, DataSyncTableRouteDTO resolvedScope) {
        validateWriteMode(syncType, dto.getWriteMode());
        if (syncType == DataSyncType.REALTIME) {
            validateRealtimeDatasourceTypes(dto.getSourceDataSourceId(), dto.getTargetDataSourceId());
        }
        validateRouteTables(
                dto.getSourceDataSourceId(), dto.getTargetDataSourceId(), resolvedScope, syncType, dto.getWriteMode());
    }

    private void validateRealtimeDatasourceTypes(String sourceDataSourceId, String targetDataSourceId) {
        DataSourceVO source = dataSourceService.queryDataSource(sourceDataSourceId);
        DataSourceVO target = dataSourceService.queryDataSource(targetDataSourceId);
        if (!"MYSQL".equals(source.getDbType())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "实时同步来源数据源仅支持 MYSQL");
        }
        if (!REALTIME_TARGET_TYPES.contains(target.getDbType())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "实时同步目标数据源仅支持 MYSQL / POSTGRE_SQL / ORACLE");
        }
    }

    private void validateRouteTables(
            String sourceDataSourceId,
            String targetDataSourceId,
            DataSyncTableRouteDTO scope,
            DataSyncType syncType,
            DataSyncWriteMode writeMode) {
        DataSourceTablePathDTO sourcePath =
                tablePath(scope.getSourceDatabase(), scope.getSourceSchema(), scope.getSourceTable());
        DataSourceTablePathDTO targetPath =
                tablePath(scope.getTargetDatabase(), scope.getTargetSchema(), scope.getTargetTable());
        dataSourceService.queryCatalogTable(sourceDataSourceId, sourcePath);
        if (dataSourceService.findCatalogTable(targetDataSourceId, targetPath).isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.TARGET_TABLE_NOT_FOUND);
        }

        TableSchema sourceSchema = dataSourceService.queryTableSchema(sourceDataSourceId, sourcePath);
        TableSchema targetSchema = dataSourceService.queryTableSchema(targetDataSourceId, targetPath);
        Map<String, Column> sourceByName = schemaColumns(sourceSchema);
        Map<String, Column> targetByName = schemaColumns(targetSchema);

        for (Column source : sourceSchema.columns()) {
            Column target = targetByName.get(source.name().toLowerCase(Locale.ROOT));
            if (target == null) {
                throw new DataSyncException(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "目标表缺少同名字段：" + source.name());
            }
            if (!JdbcSchemaCompatibility.isCompatible(source, target)) {
                throw new DataSyncException(
                        DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "来源与目标字段类型不兼容：" + source.name());
            }
        }
        for (Column target : targetSchema.columns()) {
            if (!sourceByName.containsKey(target.name().toLowerCase(Locale.ROOT)) && !target.nullable()) {
                throw new DataSyncException(
                        DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "目标表存在未匹配的必填字段：" + target.name());
            }
        }
        if (syncType == DataSyncType.REALTIME || writeMode == DataSyncWriteMode.UPSERT) {
            Set<String> sourceKeys = normalizedKeys(sourceSchema.primaryKeys());
            Set<String> targetKeys = normalizedKeys(targetSchema.primaryKeys());
            if (sourceKeys.isEmpty() || !sourceKeys.equals(targetKeys)) {
                throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "更新写入要求来源与目标表主键同名且完整");
            }
        }
    }

    private Map<String, Column> schemaColumns(TableSchema schema) {
        Map<String, Column> columns = new LinkedHashMap<>();
        for (Column column : schema.columns()) {
            Column previous = columns.putIfAbsent(column.name().toLowerCase(Locale.ROOT), column);
            if (previous != null) {
                throw new DataSyncException(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "表结构存在大小写不敏感的重名字段");
            }
        }
        return columns;
    }

    private Set<String> normalizedKeys(List<String> keys) {
        Set<String> normalized = new HashSet<>();
        for (String key : keys) {
            normalized.add(key.toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private DataSourceTablePathDTO tablePath(String database, String schema, String table) {
        String tableName = StringUtils.trimToNull(table);
        if (tableName == null) throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "表名称不能为空");

        DataSourceTablePathDTO path = new DataSourceTablePathDTO();
        path.setDatabase(StringUtils.trimToNull(database));
        path.setSchema(StringUtils.trimToNull(schema));
        path.setTable(tableName);
        return path;
    }

    private String scopeValue(String boundValue, String requestedValue) {
        String bound = StringUtils.trimToNull(boundValue);
        return bound != null ? bound : StringUtils.trimToNull(requestedValue);
    }

    private DataSyncWriteMode requireWriteMode(DataSyncWriteMode writeMode) {
        if (writeMode == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "写入方式不能为空");
        }
        return writeMode;
    }

    private void validateWriteMode(DataSyncType syncType, DataSyncWriteMode writeMode) {
        DataSyncWriteMode resolved = requireWriteMode(writeMode);
        if (syncType == DataSyncType.REALTIME && resolved != DataSyncWriteMode.APPEND) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK, "REALTIME 当前固定使用 APPEND 写入方式");
        }
    }
}
