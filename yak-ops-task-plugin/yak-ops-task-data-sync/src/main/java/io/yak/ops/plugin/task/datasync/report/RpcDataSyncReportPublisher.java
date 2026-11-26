package io.yak.ops.plugin.task.datasync.report;

import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.extract.master.IDataSyncReportListener;
import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;
import io.yak.ops.plugin.task.api.TaskExecutionContext;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/** Worker-to-Master RPC publisher with a durable outbox for final reports. */
@Slf4j
public class RpcDataSyncReportPublisher extends AbstractDataSyncReportPublisher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long OUTBOX_RETRY_INTERVAL_SECONDS = 30L;
    private static final ScheduledExecutorService RETRY_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "yak-data-sync-report-outbox");
                thread.setDaemon(true);
                return thread;
            });
    private static final Set<Path> SCHEDULED_DIRECTORIES = ConcurrentHashMap.newKeySet();
    private static final ReportTransport RPC_TRANSPORT = RpcDataSyncReportPublisher::publishRpc;

    private final TaskExecutionContext context;
    private final Path outboxDirectory;
    private final ReportTransport transport;
    private final boolean retrySchedulingEnabled;

    public RpcDataSyncReportPublisher(TaskExecutionContext context) {
        this(context, defaultOutboxDirectory(), RPC_TRANSPORT, true);
    }

    RpcDataSyncReportPublisher(
                               TaskExecutionContext context,
                               Path outboxDirectory,
                               ReportTransport transport,
                               boolean retrySchedulingEnabled) {
        this.context = context;
        this.outboxDirectory = outboxDirectory.toAbsolutePath().normalize();
        this.transport = transport;
        this.retrySchedulingEnabled = retrySchedulingEnabled;
        if (retrySchedulingEnabled) {
            scheduleRetries(this.outboxDirectory);
        }
    }

    @Override
    protected DataSyncReportAck doPublish(DataSyncReportRequest request) throws Exception {
        return transport.publish(resolveMasterAddress(), request);
    }

    @Override
    protected DataSyncReportAck onDeliveryFailure(
                                                  DataSyncReportRequest request, Exception exception)
            throws Exception {
        if (request == null || !"FINAL".equals(request.getReportType())) {
            throw exception;
        }

        String masterAddress = resolveMasterAddress();
        persist(new OutboxEntry(masterAddress, request));
        if (retrySchedulingEnabled) {
            scheduleRetries(outboxDirectory);
        }
        log.error("Final CDC report queued in durable outbox, executionId={}, sequence={}, directory={}",
                request.getExecutionId(), request.getSequence(), outboxDirectory, exception);
        return DataSyncReportAck.accepted("QUEUED", request.getSequence());
    }

    void retryPendingReports() {
        retryPendingReports(outboxDirectory, transport);
    }

    private String resolveMasterAddress() {
        String masterAddress = context == null ? null : context.getWorkflowInstanceHost();
        if (masterAddress == null || masterAddress.trim().isEmpty()) {
            throw new IllegalStateException("Master address is missing from task execution context");
        }
        return masterAddress;
    }

    private void persist(OutboxEntry entry) throws IOException {
        Files.createDirectories(outboxDirectory);
        String fileName = fileKey(entry.getRequest()) + ".json";
        Path target = outboxDirectory.resolve(fileName);
        Path temporary = Files.createTempFile(outboxDirectory, fileName, ".tmp");
        try {
            OBJECT_MAPPER.writeValue(temporary.toFile(), entry);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void scheduleRetries(Path directory) {
        Path normalized = directory.toAbsolutePath().normalize();
        if (SCHEDULED_DIRECTORIES.add(normalized)) {
            RETRY_EXECUTOR.scheduleWithFixedDelay(
                    () -> retryPendingReports(normalized, RPC_TRANSPORT),
                    5L, OUTBOX_RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static void retryPendingReports(Path directory, ReportTransport transport) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> retryPendingReport(path, transport));
        } catch (IOException exception) {
            log.warn("Failed to scan CDC report outbox directory={}", directory, exception);
        }
    }

    private static void retryPendingReport(Path file, ReportTransport transport) {
        try {
            OutboxEntry entry = OBJECT_MAPPER.readValue(file.toFile(), OutboxEntry.class);
            if (entry.getMasterAddress() == null || entry.getRequest() == null) {
                log.error("Invalid CDC report outbox entry retained for inspection: {}", file);
                return;
            }
            DataSyncReportAck ack = transport.publish(entry.getMasterAddress(), entry.getRequest());
            if (ack != null && ack.isAccepted()) {
                Files.deleteIfExists(file);
                log.info("Queued final CDC report delivered, executionId={}, sequence={}",
                        entry.getRequest().getExecutionId(), entry.getRequest().getSequence());
            } else {
                log.warn("Master did not accept queued final CDC report, executionId={}, sequence={}, result={}",
                        entry.getRequest().getExecutionId(), entry.getRequest().getSequence(),
                        ack == null ? "EMPTY_ACK" : ack.getResult());
            }
        } catch (Exception exception) {
            log.warn("Failed to deliver queued final CDC report, file={}", file, exception);
        }
    }

    private static String fileKey(DataSyncReportRequest request) {
        String value = request.getExecutionId() + "-" + request.getSequence();
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                key.append(String.format("%02x", item & 0xff));
            }
            return key.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create CDC report outbox key", exception);
        }
    }

    private static Path defaultOutboxDirectory() {
        String configured = System.getProperty("yak.ops.data-sync.report.outbox-dir");
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured);
        }
        String userHome = System.getProperty("user.home", System.getProperty("java.io.tmpdir"));
        return Paths.get(userHome, ".yak-ops", "data-sync-report-outbox");
    }

    private static DataSyncReportAck publishRpc(String masterAddress, DataSyncReportRequest request) {
        return Clients.withService(IDataSyncReportListener.class)
                .withHost(masterAddress)
                .publishReport(request);
    }

    @FunctionalInterface
    interface ReportTransport {

        DataSyncReportAck publish(String masterAddress, DataSyncReportRequest request) throws Exception;
    }

    /** JSON entry persisted locally until Master acknowledges the final report. */
    public static class OutboxEntry {

        private String masterAddress;
        private DataSyncReportRequest request;

        public OutboxEntry() {
        }

        public OutboxEntry(String masterAddress, DataSyncReportRequest request) {
            this.masterAddress = masterAddress;
            this.request = request;
        }

        public String getMasterAddress() {
            return masterAddress;
        }

        public void setMasterAddress(String masterAddress) {
            this.masterAddress = masterAddress;
        }

        public DataSyncReportRequest getRequest() {
            return request;
        }

        public void setRequest(DataSyncReportRequest request) {
            this.request = request;
        }
    }
}
