package io.yak.ops.business.datasync.execution.planning.target;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.business.datasync.schema.LogicalTable;
import io.yak.ops.business.datasync.schema.catalog.SourceTableIntrospector;
import io.yak.ops.business.datasync.schema.mapping.ResolvedSchemaMapping;
import io.yak.ops.business.datasync.schema.mapping.SchemaColumnMapping;
import io.yak.ops.business.datasync.schema.mapping.SchemaMappingResolver;
import io.yak.ops.business.datasync.schema.target.TargetSchemaCompatibility;
import io.yak.ops.business.datasync.schema.target.TargetSchemaCompatibilityResult;
import io.yak.ops.business.datasync.schema.target.TargetTablePlan;
import io.yak.ops.business.datasync.schema.target.TargetTablePlanner;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncMappingVO;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.util.ObjectUtils;
import io.yak.ops.flow.api.row.YakTableSchema;
import jakarta.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Execution 启动前的目标表 Runtime Preflight。
 *
 * <p>每个 Attempt 都重新读取 Catalog：目标存在时做 Schema Compatibility；目标不存在时仅在 definitionSnapshot
 * 显式允许 autoCreateTable 且 TargetTablePlan supported 时执行受控 CREATE TABLE。建表后必须重新 introspect，
 * 禁止直接相信生成计划。</p>
 *
 * @author weifuwan
 * @since 2026-10-04
 */
@Component
public class TargetTablePreflight {

    private final SchemaMappingResolver schemaMappingResolver = new SchemaMappingResolver();

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private SourceTableIntrospector sourceTableIntrospector;

    @Resource
    private TargetTablePlanner targetTablePlanner;

    @Resource
    private TargetTableDdlExecutor ddlExecutor;

    public TargetTablePreflightResult prepare(DataSyncDefinitionSnapshotVO snapshot, int timeoutSeconds) {
        ObjectUtils.requireNonNull(snapshot, "definition snapshot must not be null");
        DataSyncEndpointSnapshotVO sourceEndpoint =
                ObjectUtils.requireNonNull(snapshot.getSource(), "source endpoint must not be null");
        DataSyncEndpointSnapshotVO targetEndpoint =
                ObjectUtils.requireNonNull(snapshot.getTarget(), "target endpoint must not be null");

        LogicalTable sourceLogicalTable = sourceTableIntrospector.introspect(
                sourceEndpoint.getDataSourceId(),
                sourceEndpoint.getDatabase(),
                sourceEndpoint.getSchema(),
                sourceEndpoint.getTable());
        ResolvedSchemaMapping resolvedMapping = resolveSchemaMapping(sourceLogicalTable, snapshot.getMapping());
        validateRealtimeSourcePrimaryKeys(snapshot, sourceLogicalTable, resolvedMapping);
        DataSourceTablePathDTO targetPath = tablePath(targetEndpoint);

        Optional<DataSourceCatalogTableVO> targetTable =
                dataSourceService.findCatalogTable(targetEndpoint.getDataSourceId(), targetPath);
        boolean created = false;
        if (targetTable.isEmpty()) {
            if (!Boolean.TRUE.equals(snapshot.getAutoCreateTable())) {
                throw new DataSyncException(
                        DataSyncErrorCode.TARGET_TABLE_NOT_FOUND, targetEndpoint.getTable() + "；请先创建目标表或开启自动建表");
            }
            validateAutoCreatePrimaryKeys(snapshot, sourceLogicalTable, resolvedMapping);
            created = createTargetTable(resolvedMapping.targetTable(), targetEndpoint, targetPath, timeoutSeconds);
            targetTable = dataSourceService.findCatalogTable(targetEndpoint.getDataSourceId(), targetPath);
            if (targetTable.isEmpty()) {
                throw new DataSyncException(
                        DataSyncErrorCode.TARGET_TABLE_CREATE_FAILED,
                        "DDL 执行后 Catalog 仍未发现目标表：" + targetEndpoint.getTable());
            }
        }

        List<DataSourceCatalogColumnVO> targetColumns =
                dataSourceService.queryCatalogColumns(targetEndpoint.getDataSourceId(), targetPath);
        TargetSchemaCompatibilityResult compatibility =
                TargetSchemaCompatibility.check(resolvedMapping.targetTable(), targetColumns);
        if (!compatibility.compatible()) {
            throw new DataSyncException(
                    DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, String.join("；", compatibility.issues()));
        }

        validateTargetPrimaryKeyContract(snapshot, resolvedMapping, compatibility.targetWriteSchema());
        return new TargetTablePreflightResult(
                resolvedMapping.sourceTable().toRuntimeSchema(), compatibility.targetWriteSchema(), created);
    }

    private boolean createTargetTable(
            LogicalTable targetLogicalTable,
            DataSyncEndpointSnapshotVO targetEndpoint,
            DataSourceTablePathDTO targetPath,
            int timeoutSeconds) {
        String targetType = targetEndpoint.getDataSourceType();
        if (targetType == null || targetType.isBlank()) {
            DataSourceVO targetDataSource = dataSourceService.queryDataSource(targetEndpoint.getDataSourceId());
            targetType = targetDataSource.getDbType();
        }

        TargetTablePlan plan = targetTablePlanner.plan(
                targetLogicalTable,
                targetType,
                targetPath.getDatabase(),
                targetPath.getSchema(),
                targetPath.getTable());
        if (!plan.supported()) {
            throw new DataSyncException(
                    DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, String.join("；", plan.unsupportedReasons()));
        }

        try {
            ddlExecutor.createTable(
                    dataSourceService.resolveRuntimeConnection(targetEndpoint.getDataSourceId()),
                    plan.targetPath(),
                    targetLogicalTable,
                    timeoutSeconds);
            return true;
        } catch (Exception exception) {
            Optional<DataSourceCatalogTableVO> concurrentTable =
                    dataSourceService.findCatalogTable(targetEndpoint.getDataSourceId(), targetPath);
            if (concurrentTable.isPresent()) {
                return false;
            }
            throw new DataSyncException(
                    DataSyncErrorCode.TARGET_TABLE_CREATE_FAILED, exception.getMessage(), exception);
        }
    }

    private void validateRealtimeSourcePrimaryKeys(
            DataSyncDefinitionSnapshotVO snapshot,
            LogicalTable sourceLogicalTable,
            ResolvedSchemaMapping resolvedMapping) {
        if (!DataSyncType.REALTIME.name().equals(snapshot.getSyncType())) return;

        if (sourceLogicalTable.primaryKeys().isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "实时同步来源表必须包含主键");
        }
        if (!allPrimaryKeysMapped(sourceLogicalTable, resolvedMapping)) {
            throw new DataSyncException(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "实时同步字段映射必须包含来源表全部主键");
        }
    }

    private void validateAutoCreatePrimaryKeys(
            DataSyncDefinitionSnapshotVO snapshot,
            LogicalTable sourceLogicalTable,
            ResolvedSchemaMapping resolvedMapping) {
        if (!DataSyncWriteMode.UPSERT.name().equals(snapshot.getWriteMode())) return;

        if (sourceLogicalTable.primaryKeys().isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "UPSERT 自动建表要求来源表包含主键");
        }
        if (!allPrimaryKeysMapped(sourceLogicalTable, resolvedMapping)) {
            throw new DataSyncException(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "UPSERT 自动建表要求字段映射包含来源表全部主键");
        }
    }

    private void validateTargetPrimaryKeyContract(
            DataSyncDefinitionSnapshotVO snapshot,
            ResolvedSchemaMapping resolvedMapping,
            YakTableSchema targetWriteSchema) {
        Set<String> targetPrimaryKeys = normalizedKeys(targetWriteSchema.primaryKeys());

        if (DataSyncType.REALTIME.name().equals(snapshot.getSyncType())) {
            Set<String> mappedPrimaryKeys =
                    normalizedKeys(resolvedMapping.targetTable().primaryKeys());
            if (!mappedPrimaryKeys.equals(targetPrimaryKeys)) {
                String message = snapshot.getMapping() == null ? "实时同步目标表主键必须与来源表主键一致" : "实时同步目标表主键必须与映射后的来源主键一致";
                throw new DataSyncException(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, message);
            }
            return;
        }

        if (DataSyncWriteMode.UPSERT.name().equals(snapshot.getWriteMode()) && targetPrimaryKeys.isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "UPSERT 写入要求目标表存在主键");
        }
    }

    private boolean allPrimaryKeysMapped(LogicalTable sourceLogicalTable, ResolvedSchemaMapping resolvedMapping) {
        return normalizedKeys(sourceLogicalTable.primaryKeys())
                .equals(normalizedKeys(resolvedMapping.sourceTable().primaryKeys()));
    }

    private ResolvedSchemaMapping resolveSchemaMapping(LogicalTable sourceLogicalTable, DataSyncMappingVO mapping) {
        try {
            return schemaMappingResolver.resolve(sourceLogicalTable, schemaColumnMappings(mapping));
        } catch (IllegalArgumentException exception) {
            throw new DataSyncException(
                    DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, exception.getMessage(), exception);
        }
    }

    private List<SchemaColumnMapping> schemaColumnMappings(DataSyncMappingVO mapping) {
        if (mapping == null) return null;
        return mapping.getColumns().stream()
                .map(column -> new SchemaColumnMapping(column.getSource(), column.getTarget()))
                .toList();
    }

    private Set<String> normalizedKeys(List<String> keys) {
        Set<String> result = new HashSet<>();
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                result.add(key.toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private DataSourceTablePathDTO tablePath(DataSyncEndpointSnapshotVO endpoint) {
        DataSourceTablePathDTO path = new DataSourceTablePathDTO();
        path.setDatabase(endpoint.getDatabase());
        path.setSchema(endpoint.getSchema());
        path.setTable(endpoint.getTable());
        return path;
    }
}
