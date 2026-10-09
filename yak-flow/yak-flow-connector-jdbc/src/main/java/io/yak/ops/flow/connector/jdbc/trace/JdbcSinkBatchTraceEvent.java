package io.yak.ops.flow.connector.jdbc.trace;

import io.yak.ops.flow.api.trace.RuntimeTraceEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * JDBC Sink 单次事务批次提交或失败诊断事件。
 *
 * @param timestamp 事件时间
 * @param eventType SINK_BATCH_COMMITTED / SINK_BATCH_FAILED
 * @param batchNo Writer 内单调递增的批次号
 * @param rows 当前批次行数
 * @param executeDurationMillis executeBatch 执行耗时
 * @param commitDurationMillis commit 执行耗时
 * @param failureStage 失败阶段；成功事件为空
 * @param errorType 异常类型；成功事件为空
 * @param errorMessage 异常消息；成功事件为空
 * @author weifuwan
 * @since 2026-10-03
 */
public record JdbcSinkBatchTraceEvent(
        Instant timestamp,
        JdbcTraceEventType eventType,
        long batchNo,
        long rows,
        long executeDurationMillis,
        long commitDurationMillis,
        JdbcTraceFailureStage failureStage,
        String errorType,
        String errorMessage)
        implements RuntimeTraceEvent {

    public JdbcSinkBatchTraceEvent {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        if (eventType != JdbcTraceEventType.SINK_BATCH_COMMITTED && eventType != JdbcTraceEventType.SINK_BATCH_FAILED) {
            throw new IllegalArgumentException("eventType must be a SINK_BATCH event");
        }
        if (batchNo <= 0) throw new IllegalArgumentException("batchNo must be greater than 0");
        if (rows <= 0) throw new IllegalArgumentException("rows must be greater than 0");
        if (executeDurationMillis < 0) {
            throw new IllegalArgumentException("executeDurationMillis must not be negative");
        }
        if (commitDurationMillis < 0) {
            throw new IllegalArgumentException("commitDurationMillis must not be negative");
        }
    }

    @Override
    public String type() {
        return eventType.name();
    }
}
