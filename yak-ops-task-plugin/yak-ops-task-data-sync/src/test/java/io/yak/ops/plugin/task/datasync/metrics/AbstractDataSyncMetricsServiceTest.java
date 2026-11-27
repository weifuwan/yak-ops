package io.yak.ops.plugin.task.datasync.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.yak.ops.plugin.task.datasync.DataSyncTableMapping;
import io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionHandle;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AbstractDataSyncMetricsServiceTest {

    @Test
    void aggregatesExecutionAndTableCountersWithoutDoubleCounting() throws Exception {
        Map<String, Long> rawMetrics = new LinkedHashMap<>();
        rawMetrics.put("source.numSnapshotRecords", 100L);
        rawMetrics.put("source.numInsertDMLRecords", 5L);
        rawMetrics.put("sink.numCommittedRecords", 80L);
        rawMetrics.put("source.namespace.db.schema..table.orders.numSnapshotRecords", 100L);
        rawMetrics.put("sink.namespace..schema.db.table.orders.numCommittedRecords", 80L);

        AbstractDataSyncMetricsService service = new AbstractDataSyncMetricsService() {

            @Override
            protected Map<String, Long> collectRawMetrics(DataSyncExecutionHandle handle) {
                return rawMetrics;
            }
        };

        DataSyncExecutionSnapshot snapshot = service.collect(
                DataSyncExecutionHandle.builder().executionId("execution-1").build());

        assertEquals(105L, snapshot.getReadRecords());
        assertEquals(100L, snapshot.getSnapshotRecords());
        assertEquals(5L, snapshot.getIncrementalRecords());
        assertEquals(80L, snapshot.getCommittedRecords());
        assertEquals("AVAILABLE", snapshot.getMetricsState());
        assertEquals(1, snapshot.getTables().size());
        DataSyncTableMetrics table = snapshot.getTables().get(0);
        assertNotNull(table.getSourceTable());
        assertEquals("db.orders", table.getSourceTable());
        assertEquals("db.orders", table.getSinkTable());
        assertEquals(100L, table.getReadRecords());
        assertEquals(80L, table.getCommittedRecords());
        assertEquals("AVAILABLE", table.getMetricsState());
    }


    @Test
    void correlatesDifferentSourceAndSinkNamesUsingTheConfiguredRoute() throws Exception {
        Map<String, Long> rawMetrics = new LinkedHashMap<>();
        rawMetrics.put("source.namespace.source_db.schema..table.orders.numSnapshotRecords", 10L);
        rawMetrics.put("source.namespace.source_db.schema..table.orders.numInsertDMLRecords", 2L);
        rawMetrics.put("sink.namespace..schema.target_db.table.orders_target.numCommittedRecords", 12L);

        AbstractDataSyncMetricsService service = new AbstractDataSyncMetricsService() {

            @Override
            protected Map<String, Long> collectRawMetrics(DataSyncExecutionHandle handle) {
                return rawMetrics;
            }
        };
        DataSyncTableMapping mapping = DataSyncTableMapping.builder()
                .sourceTable("source_db.orders")
                .sinkTable("target_db.orders_target")
                .build();

        DataSyncExecutionSnapshot snapshot = service.collect(
                DataSyncExecutionHandle.builder()
                        .executionId("execution-mapped")
                        .tableMappings(java.util.Collections.singletonList(mapping))
                        .build());

        assertEquals(1, snapshot.getTables().size());
        DataSyncTableMetrics table = snapshot.getTables().get(0);
        assertEquals("source_db.orders", table.getSourceTable());
        assertEquals("target_db.orders_target", table.getSinkTable());
        assertEquals(12L, table.getReadRecords());
        assertEquals(12L, table.getCommittedRecords());
        assertEquals("AVAILABLE", table.getMetricsState());
    }

    @Test
    void retainsConfiguredTableNamesWhenNoTableCountersAreAvailable() throws Exception {
        AbstractDataSyncMetricsService service = new AbstractDataSyncMetricsService() {

            @Override
            protected Map<String, Long> collectRawMetrics(DataSyncExecutionHandle handle) {
                return java.util.Collections.emptyMap();
            }
        };
        DataSyncTableMapping mapping = DataSyncTableMapping.builder()
                .sourceTable("source_db.orders")
                .sinkTable("target_db.orders_target")
                .build();

        DataSyncExecutionSnapshot snapshot = service.collect(
                DataSyncExecutionHandle.builder()
                        .executionId("execution-no-metrics")
                        .tableMappings(java.util.Collections.singletonList(mapping))
                        .build());

        assertEquals("UNAVAILABLE", snapshot.getMetricsState());
        assertEquals(1, snapshot.getTables().size());
        DataSyncTableMetrics table = snapshot.getTables().get(0);
        assertEquals("source_db.orders", table.getSourceTable());
        assertEquals("target_db.orders_target", table.getSinkTable());
        assertEquals("UNAVAILABLE", table.getMetricsState());
        assertNull(table.getReadRecords());
        assertNull(table.getCommittedRecords());
    }

    @Test
    void expandsDatabaseScopeRoutesForObservedTables() throws Exception {
        Map<String, Long> rawMetrics = new LinkedHashMap<>();
        rawMetrics.put("source.namespace.source_db.schema..table.orders.numSnapshotRecords", 5L);
        rawMetrics.put("sink.namespace..schema.target_db.table.orders.numCommittedRecords", 5L);

        AbstractDataSyncMetricsService service = new AbstractDataSyncMetricsService() {

            @Override
            protected Map<String, Long> collectRawMetrics(DataSyncExecutionHandle handle) {
                return rawMetrics;
            }
        };
        DataSyncTableMapping mapping = DataSyncTableMapping.builder()
                .sourceTable("source_db.\\.*")
                .sinkTable("target_db.<>")
                .databaseScope(true)
                .build();

        DataSyncExecutionSnapshot snapshot = service.collect(
                DataSyncExecutionHandle.builder()
                        .executionId("execution-database-scope")
                        .tableMappings(java.util.Collections.singletonList(mapping))
                        .build());

        assertEquals(1, snapshot.getTables().size());
        DataSyncTableMetrics table = snapshot.getTables().get(0);
        assertEquals("source_db.orders", table.getSourceTable());
        assertEquals("target_db.orders", table.getSinkTable());
        assertEquals(5L, table.getReadRecords());
        assertEquals(5L, table.getCommittedRecords());
    }

    @Test
    void derivesExecutionTotalsFromTableCountersWhenRootCountersAreUnavailable() throws Exception {
        Map<String, Long> rawMetrics = new LinkedHashMap<>();
        rawMetrics.put("source.namespace.db.schema..table.orders.numSnapshotRecords", 12L);
        rawMetrics.put("source.namespace.db.schema..table.customers.numSnapshotRecords", 8L);
        rawMetrics.put("sink.namespace..schema.db.table.orders.numCommittedRecords", 10L);
        rawMetrics.put("sink.namespace..schema.db.table.customers.numCommittedRecords", 8L);

        AbstractDataSyncMetricsService service = new AbstractDataSyncMetricsService() {

            @Override
            protected Map<String, Long> collectRawMetrics(DataSyncExecutionHandle handle) {
                return rawMetrics;
            }
        };

        DataSyncExecutionSnapshot snapshot = service.collect(
                DataSyncExecutionHandle.builder().executionId("execution-2").build());

        assertEquals(20L, snapshot.getReadRecords());
        assertEquals(18L, snapshot.getCommittedRecords());
        assertEquals("AVAILABLE", snapshot.getMetricsState());
    }

    @Test
    void marksTableMetricsPartialWhenOnlyOneSideIsAvailable() throws Exception {
        Map<String, Long> rawMetrics = new LinkedHashMap<>();
        rawMetrics.put("source.namespace.db.schema..table.orders.numSnapshotRecords", 12L);

        AbstractDataSyncMetricsService service = new AbstractDataSyncMetricsService() {

            @Override
            protected Map<String, Long> collectRawMetrics(DataSyncExecutionHandle handle) {
                return rawMetrics;
            }
        };

        DataSyncExecutionSnapshot snapshot = service.collect(
                DataSyncExecutionHandle.builder().executionId("execution-partial").build());

        assertEquals("PARTIAL", snapshot.getMetricsState());
        assertEquals(1, snapshot.getTables().size());
        assertEquals("PARTIAL", snapshot.getTables().get(0).getMetricsState());
        assertNull(snapshot.getTables().get(0).getCommittedRecords());
    }
}
