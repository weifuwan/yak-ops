package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.flow.runtime.ExecutionStatus;

/**
 * 一次 Route Runtime 的终态、观测指标和失败信息，不代表跨数据库 Exactly-once。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
record RouteExecutionOutcome(
        ExecutionStatus status, long readRows, long writeRows, Throwable failure, boolean runtimeStarted) {}
