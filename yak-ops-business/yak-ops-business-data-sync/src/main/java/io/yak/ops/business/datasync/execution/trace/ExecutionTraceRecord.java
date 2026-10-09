package io.yak.ops.business.datasync.execution.trace;

import java.time.Instant;
import java.util.List;

/**
 * 文件 Trace Store 使用的稳定 JSONL 记录格式。
 *
 * @param version Trace Schema 版本
 * @param timestamp 事件时间
 * @param attemptId Attempt ID
 * @param attemptNo Attempt 序号
 * @param type Runtime Trace Event 类型
 * @param side SOURCE / SINK
 * @param splitId Source Split ID
 * @param workerName Reader Worker
 * @param sql Connector 生成的 SQL 模板
 * @param parameters Connector 生成的数值 Split 边界参数
 * @param splitColumn 数值 Split 字段
 * @param lowerBoundInclusive Split 下界
 * @param upperBoundInclusive Split 上界
 * @param batchSize Sink 配置的 Batch Size
 * @param saveMode Sink Save Mode
 * @param writeMode Sink Write Mode
 * @param batchNo Sink Batch 序号
 * @param rows 当前 Split / Batch 行数
 * @param durationMillis Source Split 总耗时
 * @param executeDurationMillis Sink executeBatch 耗时
 * @param commitDurationMillis Sink commit 耗时
 * @param failureStage 失败阶段
 * @param errorType 异常类型
 * @param errorMessage 已脱敏异常消息
 * @author weifuwan
 * @since 2026-10-03
 */
public record ExecutionTraceRecord(
        int version,
        Instant timestamp,
        String attemptId,
        int attemptNo,
        String type,
        ExecutionTraceSide side,
        String splitId,
        String workerName,
        String sql,
        List<Long> parameters,
        String splitColumn,
        Long lowerBoundInclusive,
        Long upperBoundInclusive,
        Integer batchSize,
        String saveMode,
        String writeMode,
        Long batchNo,
        Long rows,
        Long durationMillis,
        Long executeDurationMillis,
        Long commitDurationMillis,
        String failureStage,
        String errorType,
        String errorMessage) {}
