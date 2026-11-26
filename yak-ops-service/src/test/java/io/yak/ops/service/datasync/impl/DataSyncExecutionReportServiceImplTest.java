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
import io.yak.ops.dao.repository.DataSyncTableReportDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.extract.master.transportor.DataSyncExecutionMetrics;
import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;

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
