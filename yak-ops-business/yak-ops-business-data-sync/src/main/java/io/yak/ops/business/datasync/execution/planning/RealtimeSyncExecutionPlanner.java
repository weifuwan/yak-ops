package io.yak.ops.business.datasync.execution.planning;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.execution.planning.target.TargetTablePreflight;
import io.yak.ops.business.datasync.execution.planning.target.TargetTablePreflightResult;
import io.yak.ops.business.datasync.execution.realtime.RealtimeSyncStateNamespace;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRealtimeConfigVO;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.util.ObjectUtils;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.cdc.mysql.source.MySqlCdcSource;
import io.yak.ops.flow.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSinkConfig;
import io.yak.ops.flow.connector.jdbc.JdbcWriteMode;
import io.yak.ops.flow.connector.jdbc.sink.JdbcSink;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import jakarta.annotation.Resource;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 将 REALTIME 实例定义快照解析为 MySQL CDC Source + JDBC Changelog Sink 执行计划。
 *
 * <p>CDC state 目录与 Debezium engine identity 由 RealtimeSyncStateNamespace 按 Workspace / Task / definitionVersion
 * 稳定分配；MySQL replication serverId 由执行生命周期显式传入。</p>
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Component
public class RealtimeSyncExecutionPlanner {

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private TargetTablePreflight targetTablePreflight;

    @Resource
    private RealtimeSyncStateNamespace stateNamespace;

    public RealtimeSyncExecutionPlan plan(String workspaceId, DataSyncDefinitionSnapshotVO snapshot, long serverId) {
        ObjectUtils.requireNonNull(workspaceId, "workspace id must not be null");
        ObjectUtils.requireNonNull(snapshot, "definition snapshot must not be null");
        if (!DataSyncType.REALTIME.name().equals(snapshot.getSyncType())) {
            throw new IllegalArgumentException("realtime planner requires REALTIME snapshot");
        }

        DataSyncEndpointSnapshotVO sourceEndpoint =
                ObjectUtils.requireNonNull(snapshot.getSource(), "source endpoint must not be null");
        DataSyncEndpointSnapshotVO targetEndpoint =
                ObjectUtils.requireNonNull(snapshot.getTarget(), "target endpoint must not be null");
        DataSyncRealtimeConfigVO realtimeConfig =
                ObjectUtils.requireNonNull(snapshot.getRealtimeConfig(), "realtime config must not be null");

        TargetTablePreflightResult targetPreflight =
                targetTablePreflight.prepare(snapshot, realtimeConfig.getTimeoutSeconds());
        YakTableSchema sourceSchema = targetPreflight.sourceSchema();
        YakTableSchema targetWriteSchema = targetPreflight.targetWriteSchema();

        JdbcConnectionProperties sourceConnection =
                requireMySqlConnection(dataSourceService.resolveRuntimeConnection(sourceEndpoint.getDataSourceId()));
        DataSourceConnection targetConnection =
                dataSourceService.resolveRuntimeConnection(targetEndpoint.getDataSourceId());

        MySqlCdcSource source = new MySqlCdcSource(new MySqlCdcSourceConfig(
                sourceConnection,
                tablePathValue(sourceEndpoint),
                sourceSchema,
                stateNamespace.stateDirectory(workspaceId, snapshot.getTaskId(), snapshot.getTaskVersion()),
                stateNamespace.engineName(workspaceId, snapshot.getTaskId(), snapshot.getTaskVersion()),
                serverId,
                realtimeConfig.getQueueCapacity(),
                realtimeConfig.getPollBatchSize(),
                realtimeConfig.getTimeoutSeconds()));
        JdbcSink sink = new JdbcSink(
                new JdbcSinkConfig(
                        targetConnection,
                        tablePathValue(targetEndpoint),
                        realtimeConfig.getWriteBatchSize(),
                        realtimeConfig.getTimeoutSeconds(),
                        JdbcWriteMode.CHANGELOG),
                targetWriteSchema);
        return new RealtimeSyncExecutionPlan(
                source, sink, sourceSchema, Duration.ofSeconds(realtimeConfig.getCheckpointIntervalSeconds()));
    }

    private JdbcConnectionProperties requireMySqlConnection(DataSourceConnection connection) {
        if (!(connection instanceof JdbcConnectionProperties jdbcConnection)
                || !"MYSQL".equals(jdbcConnection.type())) {
            throw new IllegalArgumentException("realtime sync source runtime connection must be MYSQL JDBC");
        }
        return jdbcConnection;
    }

    private DataSourceTablePath tablePathValue(DataSyncEndpointSnapshotVO endpoint) {
        return new DataSourceTablePath(endpoint.getDatabase(), endpoint.getSchema(), endpoint.getTable());
    }
}
