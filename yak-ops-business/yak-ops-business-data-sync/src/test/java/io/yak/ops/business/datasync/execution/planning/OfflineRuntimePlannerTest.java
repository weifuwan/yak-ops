package io.yak.ops.business.datasync.execution.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.yak.ops.business.datasync.schema.LogicalColumn;
import io.yak.ops.business.datasync.schema.LogicalTable;
import io.yak.ops.common.bean.vo.datasync.DataSyncRuntimeConfigVO;
import io.yak.ops.common.enums.datasync.DataSyncRuntimePolicy;
import io.yak.ops.flow.api.row.YakTypes;
import io.yak.ops.flow.connector.jdbc.JdbcSourceStatistics;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineRuntimePlannerTest {

    private final OfflineRuntimePlanner planner = new OfflineRuntimePlanner();

    @Test
    void shouldPlanMediumTableWithTwoReaders() {
        OfflineRuntimePlan plan = planner.planFromStatistics(
                narrowTable(), "MYSQL", autoConfig(), new JdbcSourceStatistics("id", 1, 800_000, 800_000));

        assertEquals(DataSyncRuntimePolicy.AUTO, plan.effectiveConfig().getPolicy());
        assertEquals(250_000L, plan.effectiveConfig().getSplitSize());
        assertEquals(2, plan.effectiveConfig().getSourceParallelism());
        assertEquals(1_000, plan.effectiveConfig().getReadBatchSize());
        assertEquals(1_000, plan.effectiveConfig().getWriteBatchSize());
        assertEquals(800_000L, plan.summary().getSourceRowCount());
        assertEquals(4, plan.summary().getSplitCount());
        assertEquals("id", plan.summary().getSplitColumn());
        assertEquals(true, plan.summary().getStatisticsAvailable());
    }

    @Test
    void shouldScaleLargeAndHugeTableParallelismConservatively() {
        OfflineRuntimePlan large = planner.planFromStatistics(
                narrowTable(), "POSTGRE_SQL", autoConfig(), new JdbcSourceStatistics("id", 1, 5_000_000, 5_000_000));
        OfflineRuntimePlan huge = planner.planFromStatistics(
                narrowTable(), "MYSQL", autoConfig(), new JdbcSourceStatistics("id", 1, 50_000_000, 50_000_000));

        assertEquals(500_000L, large.effectiveConfig().getSplitSize());
        assertEquals(10, large.summary().getSplitCount());
        assertEquals(4, large.effectiveConfig().getSourceParallelism());

        assertEquals(1_000_000L, huge.effectiveConfig().getSplitSize());
        assertEquals(50, huge.summary().getSplitCount());
        assertEquals(8, huge.effectiveConfig().getSourceParallelism());
    }

    @Test
    void shouldKeepWholeTableReadWhenStatisticsAreUnavailable() {
        OfflineRuntimePlan plan = planner.planFromStatistics(narrowTable(), "MYSQL", autoConfig(), null);

        assertNull(plan.effectiveConfig().getSplitSize());
        assertEquals(1, plan.effectiveConfig().getSourceParallelism());
        assertEquals(1, plan.summary().getSplitCount());
        assertEquals("id", plan.summary().getSplitColumn());
        assertEquals(false, plan.summary().getStatisticsAvailable());
    }

    @Test
    void shouldReduceBatchSizeForWideRowsAndOracleTarget() {
        OfflineRuntimePlan mysql = planner.planFromStatistics(
                wideTable(), "MYSQL", autoConfig(), new JdbcSourceStatistics("id", 1, 80_000, 80_000));
        OfflineRuntimePlan oracle = planner.planFromStatistics(
                wideTable(), "ORACLE", autoConfig(), new JdbcSourceStatistics("id", 1, 80_000, 80_000));

        assertEquals(502, mysql.effectiveConfig().getWriteBatchSize());
        assertEquals(251, oracle.effectiveConfig().getWriteBatchSize());
        assertNull(mysql.effectiveConfig().getSplitSize());
        assertEquals(1, mysql.effectiveConfig().getSourceParallelism());
    }

    @Test
    void shouldGrowSplitSizeToKeepPlannedSplitCountBounded() {
        long rowCount = 10_000_000_000L;
        Long splitSize = OfflineRuntimePlanner.splitSize(rowCount);

        assertEquals(10_000_000L, splitSize);
        assertEquals(1_000, OfflineRuntimePlanner.splitCount(rowCount, splitSize));
        assertEquals(8, OfflineRuntimePlanner.sourceParallelism(1_000));
    }

    private DataSyncRuntimeConfigVO autoConfig() {
        DataSyncRuntimeConfigVO config = new DataSyncRuntimeConfigVO();
        config.setPolicy(DataSyncRuntimePolicy.AUTO);
        config.setFetchSize(500);
        config.setReadBatchSize(500);
        config.setWriteBatchSize(500);
        config.setSourceParallelism(1);
        config.setTimeoutSeconds(30);
        return config;
    }

    private LogicalTable narrowTable() {
        return new LogicalTable(
                "source_table",
                null,
                1,
                List.of(
                        new LogicalColumn("id", YakTypes.BIGINT, false, null, null),
                        new LogicalColumn("name", YakTypes.STRING, true, 64, null),
                        new LogicalColumn("amount", YakTypes.decimal(18, 2), true, null, null)),
                List.of("id"));
    }

    private LogicalTable wideTable() {
        return new LogicalTable(
                "source_table",
                null,
                1,
                List.of(
                        new LogicalColumn("id", YakTypes.BIGINT, false, null, null),
                        new LogicalColumn("payload", YakTypes.STRING, true, 2_000, null)),
                List.of("id"));
    }
}
