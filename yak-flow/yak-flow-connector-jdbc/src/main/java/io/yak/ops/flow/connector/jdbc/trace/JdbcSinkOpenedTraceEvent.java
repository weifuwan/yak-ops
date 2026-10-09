package io.yak.ops.flow.connector.jdbc.trace;

import io.yak.ops.flow.api.trace.RuntimeTraceEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * JDBC Sink Writer 打开后的固定写入诊断信息。
 *
 * @param timestamp 事件时间
 * @param sql 写入 SQL 模板，不包含具体业务数据
 * @param batchSize 配置的批次大小
 * @param saveMode 目标已有数据处理方式
 * @param writeMode 行级写入方式
 * @author weifuwan
 * @since 2026-10-03
 */
public record JdbcSinkOpenedTraceEvent(Instant timestamp, String sql, int batchSize, String saveMode, String writeMode)
        implements RuntimeTraceEvent {

    public JdbcSinkOpenedTraceEvent {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(sql, "sql must not be null");
        Objects.requireNonNull(saveMode, "saveMode must not be null");
        Objects.requireNonNull(writeMode, "writeMode must not be null");
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be greater than 0");
    }

    @Override
    public String type() {
        return JdbcTraceEventType.SINK_OPENED.name();
    }
}
