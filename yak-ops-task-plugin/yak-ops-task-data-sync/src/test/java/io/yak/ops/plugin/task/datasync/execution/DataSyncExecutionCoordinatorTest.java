package io.yak.ops.plugin.task.datasync.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.datasync.metrics.DataSyncExecutionSnapshot;
import io.yak.ops.plugin.task.datasync.metrics.DataSyncMetricsService;
import io.yak.ops.plugin.task.datasync.metrics.DataSyncTableMetrics;
import io.yak.ops.plugin.task.datasync.report.DataSyncReportPublisher;

import java.util.Collections;

import org.apache.flink.cdc.composer.flink.FlinkPipelineExecution;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class DataSyncExecutionCoordinatorTest {

    @Test
    void publishesFinalReportBeforeReleasingMiniCluster() throws Exception {
        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskInstanceId(42);

        DataSyncExecutionService executionService = mock(DataSyncExecutionService.class);
        DataSyncMetricsService metricsService = mock(DataSyncMetricsService.class);
        DataSyncReportPublisher reportPublisher = mock(DataSyncReportPublisher.class);
        FlinkPipelineExecution pipeline = mock(FlinkPipelineExecution.class);

        DataSyncExecutionHandle handle = DataSyncExecutionHandle.builder()
                .executionId("execution-1")
                .taskInstanceId(42)
                .jobId("job-1")
                .deploymentMode("LOCAL_MINICLUSTER")
                .clusterId("cluster-1")
                .restEndpoint("http://127.0.0.1:8081")
                .submittedAt(100L)
                .build();
        DataSyncExecutionSnapshot snapshot = DataSyncExecutionSnapshot.builder()
                .readRecords(10L)
                .snapshotRecords(10L)
                .committedRecords(8L)
                .metricsState("AVAILABLE")
                .collectedAt(200L)
                .build();

        when(executionService.submit(any(), any(), org.mockito.ArgumentMatchers.eq(42)))
                .thenReturn(handle);
        when(executionService.queryState(handle)).thenReturn(DataSyncExecutionState.FINISHED);
        when(metricsService.collect(handle)).thenReturn(snapshot);
        when(reportPublisher.publish(any()))
                .thenReturn(DataSyncReportAck.accepted("ACCEPTED", 1L));

        DataSyncExecutionCoordinator coordinator = new DataSyncExecutionCoordinator(
                context, () -> executionService, metricsService, reportPublisher);
        coordinator.execute(pipeline);

        ArgumentCaptor<DataSyncReportRequest> reports =
                ArgumentCaptor.forClass(DataSyncReportRequest.class);
        verify(reportPublisher, times(2)).publish(reports.capture());
        DataSyncReportRequest finalReport = reports.getAllValues().get(1);
        assertEquals("FINAL", finalReport.getReportType());
        assertEquals("FINISHED", finalReport.getExecutionState());
        assertEquals(2L, finalReport.getSequence());
        assertEquals(100L, finalReport.getSubmittedAt());

        InOrder order = inOrder(reportPublisher, executionService);
        order.verify(reportPublisher, times(2)).publish(any());
        order.verify(executionService).release(handle);
        verify(metricsService, times(2)).collect(handle);
    }

    @Test
    void marksReusedTableMetricsStaleAndPreservesTheirCollectionTime() throws Exception {
        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskInstanceId(42);

        DataSyncExecutionService executionService = mock(DataSyncExecutionService.class);
        DataSyncMetricsService metricsService = mock(DataSyncMetricsService.class);
        DataSyncReportPublisher reportPublisher = mock(DataSyncReportPublisher.class);
        FlinkPipelineExecution pipeline = mock(FlinkPipelineExecution.class);

        DataSyncExecutionHandle handle = DataSyncExecutionHandle.builder()
                .executionId("execution-stale")
                .taskInstanceId(42)
                .jobId("job-stale")
                .deploymentMode("LOCAL_MINICLUSTER")
                .submittedAt(100L)
                .build();
        DataSyncExecutionSnapshot available = DataSyncExecutionSnapshot.builder()
                .readRecords(10L)
                .committedRecords(8L)
                .metricsState("AVAILABLE")
                .collectedAt(200L)
                .tables(Collections.singletonList(DataSyncTableMetrics.builder()
                        .sourceTable("db.orders")
                        .sinkTable("db.orders")
                        .metricsState("AVAILABLE")
                        .readRecords(10L)
                        .committedRecords(8L)
                        .build()))
                .build();
        DataSyncExecutionSnapshot unavailable = DataSyncExecutionSnapshot.builder()
                .metricsState("UNAVAILABLE")
                .errorMessage("REST temporarily unavailable")
                .collectedAt(300L)
                .build();

        when(executionService.submit(any(), any(), org.mockito.ArgumentMatchers.eq(42)))
                .thenReturn(handle);
        when(executionService.queryState(handle)).thenReturn(DataSyncExecutionState.FINISHED);
        when(metricsService.collect(handle)).thenReturn(available, unavailable);
        when(reportPublisher.publish(any()))
                .thenReturn(DataSyncReportAck.accepted("ACCEPTED", 1L));

        DataSyncExecutionCoordinator coordinator = new DataSyncExecutionCoordinator(
                context, () -> executionService, metricsService, reportPublisher);
        coordinator.execute(pipeline);

        ArgumentCaptor<DataSyncReportRequest> reports =
                ArgumentCaptor.forClass(DataSyncReportRequest.class);
        verify(reportPublisher, times(2)).publish(reports.capture());
        DataSyncReportRequest finalReport = reports.getAllValues().get(1);

        assertEquals("STALE", finalReport.getMetricsState());
        assertEquals(200L, finalReport.getCollectedAt());
        assertEquals(1, finalReport.getTables().size());
        assertEquals("STALE", finalReport.getTables().get(0).getMetricsState());
        verify(executionService).release(handle);
    }
}
