package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.extract.master.transportor.DataSyncExecutionMetrics;
import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;
import io.yak.ops.extract.master.transportor.DataSyncTableMetrics;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.datasync.metrics.DataSyncExecutionSnapshot;
import io.yak.ops.plugin.task.datasync.metrics.DataSyncMetricsService;
import io.yak.ops.plugin.task.datasync.report.DataSyncReportPublisher;

import org.apache.flink.cdc.composer.flink.FlinkPipelineExecution;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

/** Coordinates CDC execution, metrics collection and report delivery without changing Workflow. */
@Slf4j
public class DataSyncExecutionCoordinator {

    private static final long STATE_POLL_INTERVAL_MILLIS = 1_000L;
    private static final long METRICS_POLL_INTERVAL_MILLIS = 5_000L;

    private final TaskExecutionContext context;
    private final DataSyncExecutionService executionService;
    private final DataSyncMetricsService metricsService;
    private final DataSyncReportPublisher reportPublisher;
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private final AtomicBoolean cancelDispatched = new AtomicBoolean();
    private final AtomicBoolean executionStarted = new AtomicBoolean();

    private volatile DataSyncExecutionHandle handle;
    private volatile boolean cancellationConfirmed;

    public DataSyncExecutionCoordinator(TaskExecutionContext context) {
        this(context,
                new MiniClusterDataSyncExecutionService(),
                new io.yak.ops.plugin.task.datasync.metrics.FlinkRestDataSyncMetricsService(),
                new io.yak.ops.plugin.task.datasync.report.RpcDataSyncReportPublisher(context));
    }

    public DataSyncExecutionCoordinator(
                                          TaskExecutionContext context,
                                          DataSyncExecutionService executionService,
                                          DataSyncMetricsService metricsService,
                                          DataSyncReportPublisher reportPublisher) {
        this.context = context;
        this.executionService = executionService;
        this.metricsService = metricsService;
        this.reportPublisher = reportPublisher;
    }

    public void execute(FlinkPipelineExecution execution) throws Exception {
        if (cancelRequested.get()) {
            cancellationConfirmed = true;
            return;
        }

        executionStarted.set(true);
        if (cancelRequested.get()) {
            cancellationConfirmed = true;
            return;
        }
        String executionId = UUID.randomUUID().toString();
        DataSyncExecutionSnapshot latestSnapshot = null;
        long sequence = 0L;
        long nextMetricsTime = 0L;
        DataSyncExecutionState finalState = DataSyncExecutionState.CREATED;
        try {
            handle = executionService.submit(execution, executionId, context.getTaskInstanceId());
            if (cancelRequested.get()) {
                dispatchCancellation();
            }

            while (true) {
                finalState = executionService.queryState(handle);
                long now = System.currentTimeMillis();
                if (latestSnapshot == null || now >= nextMetricsTime || finalState.isTerminal()) {
                    latestSnapshot = collectOrReuse(handle, latestSnapshot);
                    publish(handle, ++sequence, "SNAPSHOT", finalState, latestSnapshot);
                    nextMetricsTime = now + METRICS_POLL_INTERVAL_MILLIS;
                }

                if (finalState.isTerminal()) {
                    DataSyncExecutionSnapshot finalSnapshot = collectOrReuse(handle, latestSnapshot);
                    publish(handle, ++sequence, "FINAL", finalState, finalSnapshot);
                    if (finalState == DataSyncExecutionState.CANCELED) {
                        cancellationConfirmed = true;
                    }
                    if (finalState == DataSyncExecutionState.FAILED) {
                        throw new IllegalStateException(
                                "Flink CDC job failed: " + handle.getJobId());
                    }
                    return;
                }

                if (cancelRequested.get()) {
                    dispatchCancellation();
                }
                Thread.sleep(STATE_POLL_INTERVAL_MILLIS);
            }
        } finally {
            DataSyncExecutionHandle current = handle;
            if (current != null) {
                try {
                    executionService.release(current);
                } catch (Exception exception) {
                    log.warn("Failed to release MiniCluster, executionId={}", current.getExecutionId(), exception);
                } finally {
                    handle = null;
                }
            }
        }
    }

    public void cancel() throws InterruptedException, ExecutionException {
        cancelRequested.set(true);
        if (handle != null) {
            try {
                dispatchCancellation();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (Exception exception) {
                throw new ExecutionException(exception);
            }
        } else if (!executionStarted.get()) {
            cancellationConfirmed = true;
        }
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }

    public boolean isCancellationConfirmed() {
        return cancellationConfirmed;
    }

    private void dispatchCancellation() throws Exception {
        DataSyncExecutionHandle current = handle;
        if (current != null && cancelDispatched.compareAndSet(false, true)) {
            try {
                executionService.cancel(current);
            } catch (Exception exception) {
                cancelDispatched.set(false);
                throw exception;
            }
        }
    }

    private DataSyncExecutionSnapshot collectOrReuse(
                                                       DataSyncExecutionHandle current,
                                                       DataSyncExecutionSnapshot previous) {
        try {
            DataSyncExecutionSnapshot collected = metricsService.collect(current);
            if (previous == null) {
                return collected;
            }
            if ("UNAVAILABLE".equals(collected.getMetricsState())) {
                return previous.toBuilder()
                        .metricsState("STALE")
                        .errorMessage(collected.getErrorMessage())
                        .collectedAt(System.currentTimeMillis())
                        .build();
            }
            return collected.toBuilder()
                    .readRecords(firstNonNull(collected.getReadRecords(), previous.getReadRecords()))
                    .snapshotRecords(firstNonNull(collected.getSnapshotRecords(), previous.getSnapshotRecords()))
                    .incrementalRecords(firstNonNull(collected.getIncrementalRecords(), previous.getIncrementalRecords()))
                    .insertRecords(firstNonNull(collected.getInsertRecords(), previous.getInsertRecords()))
                    .updateRecords(firstNonNull(collected.getUpdateRecords(), previous.getUpdateRecords()))
                    .deleteRecords(firstNonNull(collected.getDeleteRecords(), previous.getDeleteRecords()))
                    .committedRecords(firstNonNull(collected.getCommittedRecords(), previous.getCommittedRecords()))
                    .ddlEvents(firstNonNull(collected.getDdlEvents(), previous.getDdlEvents()))
                    .errorRecords(firstNonNull(collected.getErrorRecords(), previous.getErrorRecords()))
                    .tables(collected.getTables() == null || collected.getTables().isEmpty()
                            ? previous.getTables() : collected.getTables())
                    .build();
        } catch (Exception exception) {
            log.warn("Failed to collect Flink CDC metrics, executionId={}",
                    current.getExecutionId(), exception);
            if (previous != null) {
                return previous.toBuilder()
                        .metricsState("STALE")
                        .errorMessage(exception.getMessage())
                        .collectedAt(System.currentTimeMillis())
                        .build();
            }
            return DataSyncExecutionSnapshot.builder()
                    .metricsState("UNAVAILABLE")
                    .errorMessage(exception.getMessage())
                    .collectedAt(System.currentTimeMillis())
                    .build();
        }
    }

    private static Long firstNonNull(Long value, Long fallback) {
        return value == null ? fallback : value;
    }

    private void publish(
                          DataSyncExecutionHandle current,
                          long sequence,
                          String reportType,
                          DataSyncExecutionState state,
                          DataSyncExecutionSnapshot snapshot) {
        DataSyncExecutionMetrics summary = DataSyncExecutionMetrics.builder()
                .readRecords(snapshot.getReadRecords())
                .snapshotRecords(snapshot.getSnapshotRecords())
                .incrementalRecords(snapshot.getIncrementalRecords())
                .insertRecords(snapshot.getInsertRecords())
                .updateRecords(snapshot.getUpdateRecords())
                .deleteRecords(snapshot.getDeleteRecords())
                .committedRecords(snapshot.getCommittedRecords())
                .ddlEvents(snapshot.getDdlEvents())
                .errorRecords(snapshot.getErrorRecords())
                .build();

        java.util.List<DataSyncTableMetrics> tables = snapshot.getTables().stream()
                .map(table -> DataSyncTableMetrics.builder()
                        .sourceTable(table.getSourceTable())
                        .sinkTable(table.getSinkTable())
                        .readRecords(table.getReadRecords())
                        .snapshotRecords(table.getSnapshotRecords())
                        .incrementalRecords(table.getIncrementalRecords())
                        .insertRecords(table.getInsertRecords())
                        .updateRecords(table.getUpdateRecords())
                        .deleteRecords(table.getDeleteRecords())
                        .committedRecords(table.getCommittedRecords())
                        .ddlEvents(table.getDdlEvents())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        DataSyncReportRequest request = DataSyncReportRequest.builder()
                .protocolVersion(1)
                .executionId(current.getExecutionId())
                .taskInstanceId(current.getTaskInstanceId())
                .jobId(current.getJobId())
                .deploymentMode(current.getDeploymentMode())
                .clusterId(current.getClusterId())
                .sequence(sequence)
                .submittedAt(current.getSubmittedAt())
                .reportType(reportType)
                .executionState(state.name())
                .metricsState(snapshot.getMetricsState())
                .collectedAt(snapshot.getCollectedAt())
                .metricsError(snapshot.getErrorMessage())
                .summary(summary)
                .tables(tables)
                .build();
        try {
            DataSyncReportAck ack = reportPublisher.publish(request);
            if (ack == null) {
                log.warn("Master returned an empty CDC report acknowledgement, executionId={}, sequence={}",
                        current.getExecutionId(), sequence);
            } else if (!ack.isAccepted()) {
                log.warn("Master rejected CDC report, executionId={}, sequence={}, result={}, message={}",
                        current.getExecutionId(), sequence, ack.getResult(), ack.getMessage());
            }
        } catch (Exception exception) {
            log.warn("Failed to publish CDC metrics, executionId={}, sequence={}, type={}",
                    current.getExecutionId(), sequence, reportType, exception);
        }
    }
}
