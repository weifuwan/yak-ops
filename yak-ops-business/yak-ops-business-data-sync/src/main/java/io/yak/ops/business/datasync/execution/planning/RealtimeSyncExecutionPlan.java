package io.yak.ops.business.datasync.execution.planning;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.cdc.mysql.source.MySqlCdcSource;
import io.yak.ops.flow.connector.jdbc.sink.JdbcSink;
import java.time.Duration;

/**
 * 一次实时同步可直接交给 YakFlow Local Execution Engine 的执行计划。
 *
 * @param source MySQL CDC 连续 Source
 * @param sink JDBC Changelog Sink
 * @param sourceSchema CDC 事件使用的来源逻辑字段结构
 * @param checkpointInterval 连续执行自动 Checkpoint 周期
 * @author weifuwan
 * @since 2026-09-28
 */
public record RealtimeSyncExecutionPlan(
        MySqlCdcSource source, JdbcSink sink, YakTableSchema sourceSchema, Duration checkpointInterval) {}
