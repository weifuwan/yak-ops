package io.yak.ops.flow.connector.jdbc.trace;

import io.yak.ops.flow.api.trace.RuntimeTraceEvent;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * JDBC Source Split 规划、读取与失败诊断事件。
 *
 * <p>parameters 只允许包含 Connector 生成的数值 Split 边界，不承载业务行数据。
 *
 * @param timestamp 事件时间
 * @param eventType Source Split 事件类型
 * @param splitId Split 稳定标识
 * @param workerName 实际 Reader Worker 名称；规划阶段为空
 * @param sql 规划出的查询 SQL；非规划事件可以为空
 * @param parameters Split SQL 的数值范围参数
 * @param splitColumn 数值分片字段；整表 Split 为空
 * @param lowerBoundInclusive 范围最小包含值
 * @param upperBoundInclusive 范围最大包含值
 * @param rows 当前 Split 已读取行数
 * @param durationMillis 当前 Split 从 Reader open 到完成或失败的耗时
 * @param failureStage 失败阶段；非失败事件为空
 * @param errorType 异常类型；非失败事件为空
 * @param errorMessage 异常消息；非失败事件为空
 * @author weifuwan
 * @since 2026-10-03
 */
public record JdbcSourceSplitTraceEvent(
        Instant timestamp,
        JdbcTraceEventType eventType,
        String splitId,
        String workerName,
        String sql,
        List<Long> parameters,
        String splitColumn,
        Long lowerBoundInclusive,
        Long upperBoundInclusive,
        long rows,
        long durationMillis,
        JdbcTraceFailureStage failureStage,
        String errorType,
        String errorMessage)
        implements RuntimeTraceEvent {

    public JdbcSourceSplitTraceEvent {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(splitId, "splitId must not be null");
        if (!eventType.name().startsWith("SOURCE_SPLIT_")) {
            throw new IllegalArgumentException("eventType must be a SOURCE_SPLIT event");
        }
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        if (rows < 0) throw new IllegalArgumentException("rows must not be negative");
        if (durationMillis < 0) throw new IllegalArgumentException("durationMillis must not be negative");
    }

    @Override
    public String type() {
        return eventType.name();
    }
}
