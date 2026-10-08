package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.dao.entity.datasync.DataSyncTableExecutionEntity;
import io.yak.ops.flow.runtime.ExecutionMetrics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多表 Root 中各 Table Execution 当前或最终 Attempt 的指标镜像，仅由所属 Root Worker 更新。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
final class MultiTableRootMetrics {

    private final Map<String, ExecutionMetrics> byTable = new HashMap<>();

    MultiTableRootMetrics(List<DataSyncTableExecutionEntity> tables) {
        for (DataSyncTableExecutionEntity table : tables) {
            if (byTable.put(
                            table.getId(),
                            new ExecutionMetrics(nonNegative(table.getReadRows()), nonNegative(table.getWriteRows())))
                    != null) {
                throw new IllegalStateException("duplicate Table Execution ID");
            }
        }
    }

    void reset(String tableExecutionId) {
        update(tableExecutionId, 0L, 0L);
    }

    void update(String tableExecutionId, long readRows, long writeRows) {
        if (!byTable.containsKey(tableExecutionId)) {
            throw new IllegalArgumentException("unknown Table Execution: " + tableExecutionId);
        }
        byTable.put(tableExecutionId, new ExecutionMetrics(readRows, writeRows));
    }

    ExecutionMetrics totals() {
        long readRows = 0L;
        long writeRows = 0L;
        for (ExecutionMetrics metrics : byTable.values()) {
            readRows = addSaturating(readRows, metrics.readRows());
            writeRows = addSaturating(writeRows, metrics.writeRows());
        }
        return new ExecutionMetrics(readRows, writeRows);
    }

    private static long nonNegative(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static long addSaturating(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
