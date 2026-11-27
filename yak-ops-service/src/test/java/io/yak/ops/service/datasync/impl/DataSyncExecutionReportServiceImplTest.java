package io.yak.ops.service.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.entity.DataSyncExecutionReport;
import io.yak.ops.dao.entity.DataSyncTableReport;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.repository.DataSyncExecutionReportDao;
import io.yak.ops.dao.repository.DataSyncRecoveryPointDao;
import io.yak.ops.dao.repository.DataSyncTableReportDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.extract.master.transportor.DataSyncExecutionMetrics;
import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;
import io.yak.ops.extract.master.transportor.DataSyncRecoveryPointReport;
import io.yak.ops.extract.master.transportor.DataSyncTableMetrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSyncExecutionReportServiceImplTest {

    @Mock
    private DataSyncExecutionReportDao executionReportDao;
    @Mock
    private DataSyncTableReportDao tableReportDao;
    @Mock
    private TaskInstanceDao taskInstanceDao;
    @Mock
    private DataSyncRecoveryPointDao recoveryPointDao;

    @InjectMocks
    private DataSyncExecutionReportServiceImpl service;

    @Test
    void acceptsSnapshotForDataSyncTaskAndPersistsExecutionReport() {
        when(taskInstanceDao.queryByIdForUpdate(42))
                .thenReturn(TaskInstance.builder().id(42).taskType("DATA_SYNC").build());
        when(executionReportDao.queryByExecutionId("execution-1")).thenReturn(null);

        DataSyncReportAck ack = service.acceptReport(request(1L, "SNAPSHOT"));

        assertTrue(ack.isAccepted());
        assertEquals("ACCEPTED", ack.getResult());
        assertEquals(1L, ack.getLatestSequence());
        verify(executionReportDao).insert(any(DataSyncExecutionReport.class));
        verify(tableReportDao, never()).insert(any());
    }

    @Test
    void acknowledgesDuplicateSnapshotAsIdempotent() {
        when(taskInstanceDao.queryByIdForUpdate(42))
                .thenReturn(TaskInstance.builder().id(42).taskType("DATA_SYNC").build());
        when(executionReportDao.queryByExecutionId("execution-1"))
                .thenReturn(DataSyncExecutionReport.builder()
                        .executionId("execution-1")
                        .taskInstanceId(42)
                        .jobId("job-1")
                        .latestSequence(1L)
                        .finalized(false)
                        .build());

        DataSyncReportAck ack = service.acceptReport(request(1L, "SNAPSHOT"));

        assertTrue(ack.isAccepted());
        assertEquals("DUPLICATE", ack.getResult());
        verify(executionReportDao, never()).updateById(any());
    }

    @Test
    void rejectsStaleSnapshotSequence() {
        when(taskInstanceDao.queryByIdForUpdate(42))
                .thenReturn(TaskInstance.builder().id(42).taskType("DATA_SYNC").build());
        when(executionReportDao.queryByExecutionId("execution-1"))
                .thenReturn(DataSyncExecutionReport.builder()
                        .executionId("execution-1")
                        .taskInstanceId(42)
                        .jobId("job-1")
                        .latestSequence(5L)
                        .finalized(false)
                        .build());

        DataSyncReportAck ack = service.acceptReport(request(4L, "SNAPSHOT"));

        assertFalse(ack.isAccepted());
        assertEquals("STALE", ack.getResult());
        assertEquals(5L, ack.getLatestSequence());
        verify(executionReportDao, never()).updateById(any());
    }

    @Test
    void finalizedExecutionCannotBeOverwrittenByLateSnapshot() {
        when(taskInstanceDao.queryByIdForUpdate(42))
                .thenReturn(TaskInstance.builder().id(42).taskType("DATA_SYNC").build());
        when(executionReportDao.queryByExecutionId("execution-1"))
                .thenReturn(DataSyncExecutionReport.builder()
                        .executionId("execution-1")
                        .taskInstanceId(42)
                        .jobId("job-1")
                        .latestSequence(3L)
                        .finalized(true)
                        .build());

        DataSyncReportAck ack = service.acceptReport(request(4L, "SNAPSHOT"));

        assertTrue(ack.isAccepted());
        assertEquals("FINAL_ALREADY_ACCEPTED", ack.getResult());
        verify(executionReportDao, never()).updateById(any());
    }

    @Test
    void locksTaskInstanceBeforeReadingExecutionReport() {
        when(taskInstanceDao.queryByIdForUpdate(42))
                .thenReturn(TaskInstance.builder().id(42).taskType("DATA_SYNC").build());
        when(executionReportDao.queryByExecutionId("execution-1")).thenReturn(null);

        service.acceptReport(request(1L, "SNAPSHOT"));

        org.mockito.InOrder order = inOrder(taskInstanceDao, executionReportDao);
        order.verify(taskInstanceDao).queryByIdForUpdate(42);
        order.verify(executionReportDao).queryByExecutionId("execution-1");
    }

    @Test
    void persistsNewCompletedCheckpointAndSupersedesPreviousCheckpoints() {
        TaskInstance taskInstance = TaskInstance.builder()
                .id(42)
                .projectCode(1001L)
                .taskCode(99L)
                .taskDefinitionVersion(3)
                .taskType("DATA_SYNC")
                .workflowInstanceId(200)
                .build();
        when(taskInstanceDao.queryByIdForUpdate(42)).thenReturn(taskInstance);
        when(executionReportDao.queryByExecutionId("execution-1")).thenReturn(null);
        when(recoveryPointDao.saveCheckpointIfAbsent(any())).thenReturn(true);

        DataSyncReportRequest request = request(1L, "SNAPSHOT").toBuilder()
                .recoveryPoint(DataSyncRecoveryPointReport.builder()
                        .recoveryPointId("execution-1:checkpoint:7")
                        .checkpointId("7")
                        .recoveryType("CHECKPOINT")
                        .restorePath("file:///checkpoints/chk-7")
                        .completedAt(300L)
                        .build())
                .build();

        service.acceptReport(request);

        verify(recoveryPointDao).saveCheckpointIfAbsent(org.mockito.ArgumentMatchers.argThat(point ->
                point.getTaskInstanceId() == 42
                        && point.getTaskCode() == 99L
                        && "CHECKPOINT".equals(point.getRecoveryType())
                        && "execution-1:checkpoint:7".equals(point.getRecoveryPointId())
                        && "file:///checkpoints/chk-7".equals(point.getRestorePath())
                        && point.getCompletedAt().getTime() == 300L));
    }

    @Test
    void linksRestoredExecutionToTheConsumedRecoveryPoint() {
        TaskInstance taskInstance = TaskInstance.builder()
                .id(52)
                .projectCode(1001L)
                .taskCode(99L)
                .taskDefinitionVersion(3)
                .taskType("DATA_SYNC")
                .workflowInstanceId(201)
                .build();
        when(taskInstanceDao.queryByIdForUpdate(52)).thenReturn(taskInstance);
        when(executionReportDao.queryByExecutionId("execution-2")).thenReturn(null);
        when(recoveryPointDao.markUsedByInstance("execution-1:checkpoint:7", 52, 201)).thenReturn(true);

        DataSyncReportRequest request = request(1L, "SNAPSHOT").toBuilder()
                .executionId("execution-2")
                .taskInstanceId(52)
                .jobId("job-2")
                .recoveredFromRecoveryPointId("execution-1:checkpoint:7")
                .build();

        service.acceptReport(request);

        verify(recoveryPointDao).markUsedByInstance("execution-1:checkpoint:7", 52, 201);
    }

    @Test
    void finalizedReportDoesNotFallBackToLatestTableMetrics() {
        when(executionReportDao.queryLatestByTaskInstanceId(42))
                .thenReturn(DataSyncExecutionReport.builder()
                        .executionId("execution-1")
                        .taskInstanceId(42)
                        .latestSequence(2L)
                        .finalSequence(2L)
                        .finalized(true)
                        .build());
        when(tableReportDao.queryByExecutionId("execution-1"))
                .thenReturn(java.util.Collections.singletonList(DataSyncTableReport.builder()
                        .executionId("execution-1")
                        .tableKey("orders")
                        .latestMetricsJson("{\"sourceTable\":\"db.orders\",\"readRecords\":10}")
                        .finalMetricsJson(null)
                        .build()));

        assertTrue(service.getLatestReport(42).getTables().isEmpty());
    }

    @Test
    void clearsTablesMissingFromTheCurrentSnapshot() {
        when(taskInstanceDao.queryByIdForUpdate(42))
                .thenReturn(TaskInstance.builder().id(42).taskType("DATA_SYNC").build());
        when(executionReportDao.queryByExecutionId("execution-1"))
                .thenReturn(DataSyncExecutionReport.builder()
                        .executionId("execution-1")
                        .taskInstanceId(42)
                        .jobId("job-1")
                        .latestSequence(1L)
                        .finalized(false)
                        .build());
        DataSyncTableReport existing = DataSyncTableReport.builder()
                .id(9L)
                .executionId("execution-1")
                .tableKey("orders")
                .latestMetricsJson("{\"sourceTable\":\"db.orders\",\"readRecords\":10}")
                .build();
        when(tableReportDao.queryByExecutionId("execution-1"))
                .thenReturn(java.util.Collections.singletonList(existing));

        service.acceptReport(request(2L, "SNAPSHOT"));

        verify(tableReportDao).clearSnapshotFields(
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(java.util.Date.class),
                org.mockito.ArgumentMatchers.eq(false));
    }


    @Test
    void advancesExistingTableReportSequenceWhenSnapshotContainsNoTableMetrics() {
        when(taskInstanceDao.queryByIdForUpdate(42))
                .thenReturn(TaskInstance.builder().id(42).taskType("DATA_SYNC").build());
        when(executionReportDao.queryByExecutionId("execution-1"))
                .thenReturn(DataSyncExecutionReport.builder()
                        .id(1L)
                        .executionId("execution-1")
                        .taskInstanceId(42)
                        .jobId("job-1")
                        .latestSequence(1L)
                        .finalized(false)
                        .build());
        when(tableReportDao.queryByExecutionId("execution-1"))
                .thenReturn(java.util.Collections.singletonList(DataSyncTableReport.builder()
                        .id(9L)
                        .executionId("execution-1")
                        .tableKey("orders")
                        .latestMetricsJson("{\"sourceTable\":\"db.orders\",\"readRecords\":10}")
                        .build()));
        when(tableReportDao.clearSnapshotFields(
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(java.util.Date.class),
                org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(1);

        DataSyncReportRequest request = request(2L, "SNAPSHOT");
        request.setTables(java.util.Collections.emptyList());
        DataSyncReportAck ack = service.acceptReport(request);

        assertTrue(ack.isAccepted());
        verify(tableReportDao).clearSnapshotFields(
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(java.util.Date.class),
                org.mockito.ArgumentMatchers.eq(false));
        verify(tableReportDao, never()).insert(any());
        verify(tableReportDao, never()).updateById(any());
    }


    @Test
    void upgradesLegacySinkOnlyTableReportWhenSourceMappingBecomesAvailable() {
        when(taskInstanceDao.queryByIdForUpdate(42))
                .thenReturn(TaskInstance.builder().id(42).taskType("DATA_SYNC").build());
        when(executionReportDao.queryByExecutionId("execution-1"))
                .thenReturn(DataSyncExecutionReport.builder()
                        .id(1L)
                        .executionId("execution-1")
                        .taskInstanceId(42)
                        .jobId("job-1")
                        .latestSequence(1L)
                        .finalized(false)
                        .build());
        DataSyncTableReport legacy = DataSyncTableReport.builder()
                .id(9L)
                .executionId("execution-1")
                .tableKey("legacy-sink-only-key")
                .sourceTable(null)
                .sinkTable("target_db.orders_target")
                .build();
        when(tableReportDao.queryByExecutionId("execution-1"))
                .thenReturn(java.util.Collections.singletonList(legacy));
        when(tableReportDao.queryByExecutionIdAndPartialTableMapping(
                "execution-1", "source_db.orders", "target_db.orders_target"))
                .thenReturn(legacy);
        when(tableReportDao.clearSnapshotFields(
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(java.util.Date.class),
                org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(1);

        DataSyncReportRequest request = request(2L, "SNAPSHOT");
        request.setTables(java.util.Collections.singletonList(DataSyncTableMetrics.builder()
                .sourceTable("source_db.orders")
                .sinkTable("target_db.orders_target")
                .metricsState("PARTIAL")
                .readRecords(10L)
                .build()));

        DataSyncReportAck ack = service.acceptReport(request);

        assertTrue(ack.isAccepted());
        assertEquals("source_db.orders", legacy.getSourceTable());
        assertEquals("target_db.orders_target", legacy.getSinkTable());
        org.junit.jupiter.api.Assertions.assertNotEquals("legacy-sink-only-key", legacy.getTableKey());
        verify(tableReportDao).updateById(legacy);
        verify(tableReportDao, never()).insert(any());
    }

    @Test
    void clearsFinalTableValuesForTablesMissingFromFinalSnapshot() {
        when(taskInstanceDao.queryByIdForUpdate(42))
                .thenReturn(TaskInstance.builder().id(42).taskType("DATA_SYNC").build());
        when(executionReportDao.queryByExecutionId("execution-1"))
                .thenReturn(DataSyncExecutionReport.builder()
                        .id(1L)
                        .executionId("execution-1")
                        .taskInstanceId(42)
                        .jobId("job-1")
                        .latestSequence(1L)
                        .finalized(false)
                        .build());
        DataSyncTableReport existing = DataSyncTableReport.builder()
                .id(9L)
                .executionId("execution-1")
                .tableKey("orders")
                .latestMetricsJson("{\"sourceTable\":\"db.orders\",\"readRecords\":10}")
                .build();
        when(tableReportDao.queryByExecutionId("execution-1"))
                .thenReturn(java.util.Collections.singletonList(existing));

        service.acceptReport(request(2L, "FINAL"));

        verify(tableReportDao).clearSnapshotFields(
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(java.util.Date.class),
                org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void returnsLatestStageObservationsInOneBatchWithoutFallingBackToOlderReports() {
        String summaryJson = "{\\"executionSummary\\":{"
                + "\\"version\\":3,"
                + "\\"observedAt\\":\\"2026-11-27T09:00:00Z\\","
                + "\\"generation\\":0,"
                + "\\"restored\\":false,"
                + "\\"counterScope\\":\\"GENERATION\\","
                + "\\"phase\\":\\"SNAPSHOT\\","
                + "\\"checkpoints\\":{\\"handoffId\\":null,\\"completedId\\":null},"
                + "\\"stages\\":[],\\"tables\\":[]}}";
        when(executionReportDao.queryLatestByTaskInstanceIds(java.util.List.of(42, 43)))
                .thenReturn(java.util.List.of(
                        DataSyncExecutionReport.builder()
                                .taskInstanceId(42)
                                .latestSnapshotJson(summaryJson)
                                .build(),
                        DataSyncExecutionReport.builder()
                                .taskInstanceId(43)
                                .latestSnapshotJson("{\\"readRecords\\":10}")
                                .build(),
                        DataSyncExecutionReport.builder()
                                .taskInstanceId(43)
                                .latestSnapshotJson(summaryJson)
                                .build()));

        java.util.Map<Integer, String> reports =
                service.getLatestExecutionReports(java.util.List.of(42, 43));

        assertTrue(reports.containsKey(42));
        assertTrue(reports.get(42).contains("SNAPSHOT"));
        assertFalse(reports.containsKey(43));
        verify(executionReportDao).queryLatestByTaskInstanceIds(java.util.List.of(42, 43));
    }

    private static DataSyncReportRequest request(long sequence, String reportType) {
        return DataSyncReportRequest.builder()
                .protocolVersion(1)
                .executionId("execution-1")
                .taskInstanceId(42)
                .jobId("job-1")
                .deploymentMode("LOCAL_MINICLUSTER")
                .sequence(sequence)
                .submittedAt(System.currentTimeMillis())
                .reportType(reportType)
                .executionState("RUNNING")
                .metricsState("AVAILABLE")
                .collectedAt(System.currentTimeMillis())
                .summary(DataSyncExecutionMetrics.builder().readRecords(10L).committedRecords(8L).build())
                .build();
    }
}
