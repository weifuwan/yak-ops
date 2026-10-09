package io.yak.ops.business.datasync.execution.trace;

/**
 * Attempt Runtime Trace 汇总快照。
 *
 * @author weifuwan
 * @since 2026-10-03
 */
public record ExecutionTraceSummarySnapshot(
        int attemptNo,
        boolean available,
        boolean complete,
        long sourceSplitCount,
        long sourceFinishedSplitCount,
        long sourceFailedSplitCount,
        long sourceRows,
        long sourceSplitDurationMillis,
        String sinkSql,
        Integer sinkBatchSize,
        String sinkSaveMode,
        String sinkWriteMode,
        long sinkCommittedBatchCount,
        long sinkFailedBatchCount,
        long sinkRows,
        long sinkExecuteDurationMillis,
        long sinkCommitDurationMillis,
        long errorCount,
        long droppedEventCount) {

    public static ExecutionTraceSummarySnapshot empty(int attemptNo) {
        return new ExecutionTraceSummarySnapshot(
                attemptNo, false, false, 0L, 0L, 0L, 0L, 0L, null, null, null, null, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }
}
