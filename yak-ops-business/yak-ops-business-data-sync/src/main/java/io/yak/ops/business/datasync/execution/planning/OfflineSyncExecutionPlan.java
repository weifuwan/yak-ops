package io.yak.ops.business.datasync.execution.planning;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.sink.JdbcSink;
import io.yak.ops.flow.connector.jdbc.source.JdbcSource;

/**
 * 一次离线同步可直接交给 YakFlow Local Execution Engine 的执行计划。
 *
 * @param source 已解析运行时连接与读取参数的 JDBC Source
 * @param sink 已解析运行时连接与写入参数的 JDBC Sink
 * @param sourceSchema Source 产出的逻辑字段结构
 * @param sourceParallelism bounded Source Reader 并行度
 * @author weifuwan
 * @since 2026-09-28
 */
public record OfflineSyncExecutionPlan(
        JdbcSource source, JdbcSink sink, YakTableSchema sourceSchema, int sourceParallelism) {}
