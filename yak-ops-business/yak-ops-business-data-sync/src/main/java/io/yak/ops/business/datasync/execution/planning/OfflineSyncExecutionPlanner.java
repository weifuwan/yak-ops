package io.yak.ops.business.datasync.execution.planning;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.execution.planning.target.TargetTablePreflight;
import io.yak.ops.business.datasync.execution.planning.target.TargetTablePreflightResult;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRuntimeConfigVO;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.util.ObjectUtils;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.trace.RuntimeTraceListener;
import io.yak.ops.flow.connector.jdbc.JdbcSaveMode;
import io.yak.ops.flow.connector.jdbc.JdbcSinkConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSourceConfig;
import io.yak.ops.flow.connector.jdbc.JdbcWriteMode;
import io.yak.ops.flow.connector.jdbc.sink.JdbcSink;
import io.yak.ops.flow.connector.jdbc.source.JdbcSource;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 将实例定义快照解析为 YakFlow 可直接执行的 JDBC Source / Sink 计划。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Component
public class OfflineSyncExecutionPlanner {

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private TargetTablePreflight targetTablePreflight;

    public OfflineSyncExecutionPlan plan(DataSyncDefinitionSnapshotVO snapshot) {
        return plan(snapshot, RuntimeTraceListener.noop());
    }

    public OfflineSyncExecutionPlan plan(DataSyncDefinitionSnapshotVO snapshot, RuntimeTraceListener traceListener) {
        ObjectUtils.requireNonNull(snapshot, "definition snapshot must not be null");
        ObjectUtils.requireNonNull(traceListener, "trace listener must not be null");
        DataSyncEndpointSnapshotVO sourceEndpoint =
                ObjectUtils.requireNonNull(snapshot.getSource(), "source endpoint must not be null");
        DataSyncEndpointSnapshotVO targetEndpoint =
                ObjectUtils.requireNonNull(snapshot.getTarget(), "target endpoint must not be null");
        DataSyncRuntimeConfigVO runtimeConfig =
                ObjectUtils.requireNonNull(snapshot.getRuntimeConfig(), "runtime config must not be null");

        TargetTablePreflightResult targetPreflight =
                targetTablePreflight.prepare(snapshot, runtimeConfig.getTimeoutSeconds());
        YakTableSchema sourceSchema = targetPreflight.sourceSchema();
        YakTableSchema targetWriteSchema = targetPreflight.targetWriteSchema();

        DataSourceConnection sourceConnection =
                dataSourceService.resolveRuntimeConnection(sourceEndpoint.getDataSourceId());
        DataSourceConnection targetConnection =
                dataSourceService.resolveRuntimeConnection(targetEndpoint.getDataSourceId());

        JdbcSource source = new JdbcSource(
                new JdbcSourceConfig(
                        sourceConnection,
                        tablePathValue(sourceEndpoint),
                        sourceSchema,
                        runtimeConfig.getFetchSize(),
                        runtimeConfig.getReadBatchSize(),
                        runtimeConfig.getTimeoutSeconds(),
                        runtimeConfig.getSplitSize()),
                traceListener);
        JdbcSink sink = new JdbcSink(
                new JdbcSinkConfig(
                        targetConnection,
                        tablePathValue(targetEndpoint),
                        runtimeConfig.getWriteBatchSize(),
                        runtimeConfig.getTimeoutSeconds(),
                        saveMode(snapshot.getWriteMode()),
                        writeMode(snapshot.getWriteMode())),
                targetWriteSchema,
                traceListener);
        return new OfflineSyncExecutionPlan(source, sink, sourceSchema, runtimeConfig.getSourceParallelism());
    }

    static JdbcSaveMode saveMode(String writeMode) {
        if (writeMode == null || writeMode.isBlank()) return JdbcSaveMode.APPEND;
        DataSyncWriteMode resolved;
        try {
            resolved = DataSyncWriteMode.valueOf(writeMode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported offline write mode: " + writeMode, exception);
        }
        return switch (resolved) {
            case APPEND, UPSERT -> JdbcSaveMode.APPEND;
            case OVERWRITE -> JdbcSaveMode.OVERWRITE;
        };
    }

    static JdbcWriteMode writeMode(String writeMode) {
        if (writeMode == null || writeMode.isBlank()) return JdbcWriteMode.INSERT;
        DataSyncWriteMode resolved;
        try {
            resolved = DataSyncWriteMode.valueOf(writeMode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported offline write mode: " + writeMode, exception);
        }
        return resolved == DataSyncWriteMode.UPSERT ? JdbcWriteMode.UPSERT : JdbcWriteMode.INSERT;
    }

    private DataSourceTablePath tablePathValue(DataSyncEndpointSnapshotVO endpoint) {
        return new DataSourceTablePath(endpoint.getDatabase(), endpoint.getSchema(), endpoint.getTable());
    }
}
