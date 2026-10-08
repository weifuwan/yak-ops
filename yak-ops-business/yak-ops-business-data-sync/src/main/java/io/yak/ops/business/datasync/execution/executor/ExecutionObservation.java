package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.flow.runtime.ExecutionMetrics;
import io.yak.ops.flow.runtime.ExecutionStatus;

/**
 * YakFlow Runtime 完成后的终态与最终一次指标快照。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
record ExecutionObservation(ExecutionStatus status, ExecutionMetrics metrics) {}
