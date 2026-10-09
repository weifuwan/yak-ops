package io.yak.ops.business.datasync.execution.trace;

/**
 * Data Sync Runtime Trace 存储边界。
 *
 * @author weifuwan
 * @since 2026-10-03
 */
public interface ExecutionTraceStore {

    /**
     * 为一个 Offline Attempt 打开 Trace 收集会话。
     */
    ExecutionTraceSession openSession(String workspaceId, String executionId, String attemptId, int attemptNo);

    /**
     * 查询 Attempt Trace 汇总。
     */
    ExecutionTraceSummarySnapshot querySummary(String workspaceId, String executionId, int attemptNo);

    /**
     * Cursor 分页查询 Source / Sink 终态 Trace。
     */
    ExecutionTracePage queryPage(
            String workspaceId,
            String executionId,
            int attemptNo,
            ExecutionTraceSide side,
            int pageSize,
            String cursor,
            String status);
}
