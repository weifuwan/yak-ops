package io.yak.ops.plugin.task.datasync.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;
import io.yak.ops.plugin.task.api.TaskExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RpcDataSyncReportPublisherTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsFinalReportAfterImmediateRetriesAndRemovesItAfterMasterAcknowledges() throws Exception {
        TaskExecutionContext context = new TaskExecutionContext();
        context.setWorkflowInstanceHost("master:12345");
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delivered = new AtomicInteger();

        RpcDataSyncReportPublisher publisher = new RpcDataSyncReportPublisher(
                context,
                temporaryDirectory,
                (masterAddress, request) -> {
                    assertEquals("master:12345", masterAddress);
                    if (attempts.incrementAndGet() <= 3) {
                        throw new IOException("Master is temporarily unavailable");
                    }
                    delivered.incrementAndGet();
                    return DataSyncReportAck.accepted("ACCEPTED", request.getSequence());
                },
                false);

        DataSyncReportAck ack = publisher.publish(report("FINAL"));

        assertTrue(ack.isAccepted());
        assertEquals("QUEUED", ack.getResult());
        assertEquals(3, attempts.get());
        assertEquals(1, countOutboxFiles());

        publisher.retryPendingReports();

        assertEquals(4, attempts.get());
        assertEquals(1, delivered.get());
        assertEquals(0, countOutboxFiles());
    }

    @Test
    void doesNotPersistFailedSnapshotReports() throws Exception {
        TaskExecutionContext context = new TaskExecutionContext();
        context.setWorkflowInstanceHost("master:12345");
        AtomicInteger attempts = new AtomicInteger();

        RpcDataSyncReportPublisher publisher = new RpcDataSyncReportPublisher(
                context,
                temporaryDirectory,
                (masterAddress, request) -> {
                    attempts.incrementAndGet();
                    throw new IOException("Master is unavailable");
                },
                false);

        IOException failure = assertThrows(IOException.class, () -> publisher.publish(report("SNAPSHOT")));

        assertEquals("Master is unavailable", failure.getMessage());
        assertEquals(3, attempts.get());
        assertEquals(0, countOutboxFiles());
    }

    private long countOutboxFiles() throws IOException {
        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json")).count();
        }
    }

    private static DataSyncReportRequest report(String type) {
        return DataSyncReportRequest.builder()
                .protocolVersion(1)
                .executionId("execution-outbox")
                .taskInstanceId(42)
                .jobId("job-outbox")
                .deploymentMode("LOCAL_MINICLUSTER")
                .sequence(7L)
                .reportType(type)
                .executionState("FINISHED")
                .metricsState("AVAILABLE")
                .summary(io.yak.ops.extract.master.transportor.DataSyncExecutionMetrics.builder()
                        .readRecords(10L)
                        .build())
                .build();
    }
}
