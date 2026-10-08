package io.yak.ops.business.datasync.execution.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.dao.entity.datasync.DataSyncTableExecutionEntity;
import io.yak.ops.flow.runtime.ExecutionMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class MultiTableRootMetricsTest {

    @Test
    void retryReplacesOnlyFailedTableAttemptMirror() {
        MultiTableRootMetrics metrics = new MultiTableRootMetrics(List.of(table("users", 10L, 10L), table("orders", 0L, 0L)));
        metrics.update("orders", 5L, 2L);
        assertEquals(new ExecutionMetrics(15L, 12L), metrics.totals());

        metrics.reset("orders");
        assertEquals(new ExecutionMetrics(10L, 10L), metrics.totals());
        metrics.update("orders", 7L, 7L);
        assertEquals(new ExecutionMetrics(17L, 17L), metrics.totals());
    }

    @Test
    void aggregateUsesSaturationAndNullMetricsDefaultToZero() {
        MultiTableRootMetrics metrics = new MultiTableRootMetrics(
                List.of(table("large", Long.MAX_VALUE, Long.MAX_VALUE), table("empty", null, null)));
        metrics.update("empty", 200L, 300L);
        assertEquals(new ExecutionMetrics(Long.MAX_VALUE, Long.MAX_VALUE), metrics.totals());
    }

    @Test
    void unknownTableAndNegativeMetricsAreRejected() {
        MultiTableRootMetrics metrics = new MultiTableRootMetrics(List.of(table("users", 0L, 0L)));
        assertThrows(IllegalArgumentException.class, () -> metrics.reset("missing"));
        assertThrows(IllegalArgumentException.class, () -> metrics.update("users", -1L, 0L));
    }

    private static DataSyncTableExecutionEntity table(String id, Long readRows, Long writeRows) {
        DataSyncTableExecutionEntity table = new DataSyncTableExecutionEntity();
        table.setId(id);
        table.setReadRows(readRows);
        table.setWriteRows(writeRows);
        return table;
    }
}
