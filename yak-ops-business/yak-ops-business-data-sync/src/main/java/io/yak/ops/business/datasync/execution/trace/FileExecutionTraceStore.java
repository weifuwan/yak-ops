package io.yak.ops.business.datasync.execution.trace;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.flow.api.trace.RuntimeTraceEvent;
import io.yak.ops.flow.api.trace.RuntimeTraceListener;
import io.yak.ops.flow.connector.jdbc.trace.JdbcSinkBatchTraceEvent;
import io.yak.ops.flow.connector.jdbc.trace.JdbcSinkOpenedTraceEvent;
import io.yak.ops.flow.connector.jdbc.trace.JdbcSourceSplitTraceEvent;
import io.yak.ops.flow.connector.jdbc.trace.JdbcTraceEventType;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 使用本地持久化目录保存 Offline Attempt Runtime Trace。
 *
 * <p>Trace 以 JSONL 分片保存；写入由有界队列异步完成，Trace Store 故障或队列拥塞不会改变数据同步结果。
 *
 * @author weifuwan
 * @since 2026-10-03
 */
@Component
public class FileExecutionTraceStore implements ExecutionTraceStore {

    private static final Logger LOG = LoggerFactory.getLogger(FileExecutionTraceStore.class);
    private static final String HOME_PROPERTY = "yak.ops.home";
    private static final String TRACE_ROOT_DIRECTORY = "data/data-sync/execution-traces";
    private static final String SUMMARY_FILE = "summary.json";
    private static final int TRACE_SCHEMA_VERSION = 1;
    private static final int QUEUE_CAPACITY = 4096;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;
    private static final long MAX_SEGMENT_BYTES = 32L * 1024L * 1024L;
    private static final long WRITER_JOIN_TIMEOUT_MILLIS = 5000L;

    private final Path rootDirectory;
    private final Map<TraceKey, FileTraceSession> activeSessions = new ConcurrentHashMap<>();

    public FileExecutionTraceStore() {
        this(resolveRootDirectory());
    }

    FileExecutionTraceStore(Path rootDirectory) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "root directory must not be null")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public synchronized ExecutionTraceSession openSession(
            String workspaceId, String executionId, String attemptId, int attemptNo) {
        TraceKey key = traceKey(workspaceId, executionId, attemptNo);
        if (activeSessions.containsKey(key)) {
            LOG.warn(
                    "Runtime Trace Attempt 已存在活动会话，忽略重复打开，workspaceId={}, executionId={}, attempt={}",
                    key.workspaceId(),
                    key.executionId(),
                    key.attemptNo());
            return ExecutionTraceSession.noop();
        }

        try {
            Path directory = attemptDirectory(key);
            Files.createDirectories(directory);
            FileTraceSession session = new FileTraceSession(key, requireSegment(attemptId, "attemptId"), directory);
            activeSessions.put(key, session);
            return session;
        } catch (RuntimeException | IOException exception) {
            LOG.warn(
                    "Runtime Trace 会话创建失败，workspaceId={}, executionId={}, attempt={}, error={}",
                    key.workspaceId(),
                    key.executionId(),
                    key.attemptNo(),
                    safeMessage(exception));
            return ExecutionTraceSession.noop();
        }
    }

    @Override
    public ExecutionTraceSummarySnapshot querySummary(String workspaceId, String executionId, int attemptNo) {
        TraceKey key = traceKey(workspaceId, executionId, attemptNo);
        FileTraceSession active = activeSessions.get(key);
        if (active != null) return active.summary(false);

        Path directory = attemptDirectory(key);
        Path summaryFile = directory.resolve(SUMMARY_FILE);
        if (Files.isRegularFile(summaryFile)) {
            try {
                return JSONUtils.parseObject(
                        Files.readString(summaryFile, StandardCharsets.UTF_8), ExecutionTraceSummarySnapshot.class);
            } catch (IOException | RuntimeException exception) {
                LOG.warn(
                        "Runtime Trace Summary 读取失败，workspaceId={}, executionId={}, attempt={}, error={}",
                        key.workspaceId(),
                        key.executionId(),
                        key.attemptNo(),
                        safeMessage(exception));
            }
        }

        if (!Files.isDirectory(directory)) return emptySummary(attemptNo);
        TraceSummaryAccumulator accumulator = new TraceSummaryAccumulator(attemptNo);
        boolean found = forEachPersistedRecord(directory, accumulator::accept);
        return found ? accumulator.snapshot(false) : emptySummary(attemptNo);
    }

    @Override
    public ExecutionTracePage queryPage(
            String workspaceId,
            String executionId,
            int attemptNo,
            ExecutionTraceSide side,
            int pageSize,
            String cursor,
            String status) {
        TraceKey key = traceKey(workspaceId, executionId, attemptNo);
        Objects.requireNonNull(side, "side must not be null");
        if (pageSize <= 0 || pageSize > 200) {
            throw new IllegalArgumentException("pageSize must be between 1 and 200");
        }
        String normalizedStatus = normalizeStatus(status);
        Path directory = attemptDirectory(key);
        if (!Files.isDirectory(directory)) return new ExecutionTracePage(List.of(), null, false);

        List<Path> segments = segmentFiles(directory);
        TraceCursor start = TraceCursor.parse(cursor);
        if (start.segmentIndex() >= segments.size()) return new ExecutionTracePage(List.of(), null, false);

        List<ExecutionTraceRecord> records = new ArrayList<>(pageSize);
        for (int segmentIndex = start.segmentIndex(); segmentIndex < segments.size(); segmentIndex++) {
            Path segment = segments.get(segmentIndex);
            int startLine = segmentIndex == start.segmentIndex() ? start.lineIndex() : 0;
            try (BufferedReader reader = Files.newBufferedReader(segment, StandardCharsets.UTF_8)) {
                String line;
                int lineIndex = 0;
                while ((line = reader.readLine()) != null) {
                    if (lineIndex < startLine) {
                        lineIndex++;
                        continue;
                    }
                    int currentLine = lineIndex++;
                    ExecutionTraceRecord record = parseRecord(line, segment);
                    if (record == null || !matches(record, side, normalizedStatus)) continue;
                    if (records.size() >= pageSize) {
                        return new ExecutionTracePage(
                                List.copyOf(records), TraceCursor.encode(segmentIndex, currentLine), true);
                    }
                    records.add(record);
                }
            } catch (IOException exception) {
                LOG.warn(
                        "Runtime Trace 分片读取失败，workspaceId={}, executionId={}, attempt={}, file={}, error={}",
                        key.workspaceId(),
                        key.executionId(),
                        key.attemptNo(),
                        segment.getFileName(),
                        safeMessage(exception));
            }
        }
        return new ExecutionTracePage(List.copyOf(records), null, false);
    }

    Path rootDirectory() {
        return rootDirectory;
    }

    private ExecutionTraceRecord toRecord(
            RuntimeTraceEvent event, String attemptId, int attemptNo, Map<String, String> sourceSqlBySplit) {
        if (event instanceof JdbcSourceSplitTraceEvent source) {
            String sql = StringUtils.trimToNull(source.sql());
            if (sql != null) sourceSqlBySplit.put(source.splitId(), sql);
            if (sql == null) sql = sourceSqlBySplit.get(source.splitId());
            return new ExecutionTraceRecord(
                    TRACE_SCHEMA_VERSION,
                    source.timestamp(),
                    attemptId,
                    attemptNo,
                    source.eventType().name(),
                    ExecutionTraceSide.SOURCE,
                    source.splitId(),
                    source.workerName(),
                    sql,
                    source.parameters(),
                    source.splitColumn(),
                    source.lowerBoundInclusive(),
                    source.upperBoundInclusive(),
                    null,
                    null,
                    null,
                    null,
                    source.rows(),
                    source.durationMillis(),
                    null,
                    null,
                    source.failureStage() == null ? null : source.failureStage().name(),
                    source.errorType(),
                    safeMessage(source.errorMessage()));
        }
        if (event instanceof JdbcSinkOpenedTraceEvent opened) {
            return new ExecutionTraceRecord(
                    TRACE_SCHEMA_VERSION,
                    opened.timestamp(),
                    attemptId,
                    attemptNo,
                    JdbcTraceEventType.SINK_OPENED.name(),
                    ExecutionTraceSide.SINK,
                    null,
                    null,
                    opened.sql(),
                    List.of(),
                    null,
                    null,
                    null,
                    opened.batchSize(),
                    opened.saveMode(),
                    opened.writeMode(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        if (event instanceof JdbcSinkBatchTraceEvent batch) {
            return new ExecutionTraceRecord(
                    TRACE_SCHEMA_VERSION,
                    batch.timestamp(),
                    attemptId,
                    attemptNo,
                    batch.eventType().name(),
                    ExecutionTraceSide.SINK,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    batch.batchNo(),
                    batch.rows(),
                    null,
                    batch.executeDurationMillis(),
                    batch.commitDurationMillis(),
                    batch.failureStage() == null ? null : batch.failureStage().name(),
                    batch.errorType(),
                    safeMessage(batch.errorMessage()));
        }
        return null;
    }

    private boolean matches(ExecutionTraceRecord record, ExecutionTraceSide side, String status) {
        if (record.side() != side) return false;
        String recordStatus;
        if (side == ExecutionTraceSide.SOURCE) {
            if (JdbcTraceEventType.SOURCE_SPLIT_FINISHED.name().equals(record.type())) {
                recordStatus = "SUCCESS";
            } else if (JdbcTraceEventType.SOURCE_SPLIT_FAILED.name().equals(record.type())) {
                recordStatus = "FAILED";
            } else {
                return false;
            }
        } else {
            if (JdbcTraceEventType.SINK_BATCH_COMMITTED.name().equals(record.type())) {
                recordStatus = "SUCCESS";
            } else if (JdbcTraceEventType.SINK_BATCH_FAILED.name().equals(record.type())) {
                recordStatus = "FAILED";
            } else {
                return false;
            }
        }
        return status == null || status.equals(recordStatus);
    }

    private String normalizeStatus(String status) {
        String value = StringUtils.trimToNull(status);
        if (value == null || "ALL".equalsIgnoreCase(value)) return null;
        value = value.toUpperCase();
        if (!"SUCCESS".equals(value) && !"FAILED".equals(value)) {
            throw new IllegalArgumentException("status only supports SUCCESS / FAILED");
        }
        return value;
    }

    private boolean forEachPersistedRecord(Path directory, java.util.function.Consumer<ExecutionTraceRecord> consumer) {
        boolean found = false;
        for (Path segment : segmentFiles(directory)) {
            try (BufferedReader reader = Files.newBufferedReader(segment, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ExecutionTraceRecord record = parseRecord(line, segment);
                    if (record == null) continue;
                    found = true;
                    consumer.accept(record);
                }
            } catch (IOException exception) {
                LOG.warn(
                        "Runtime Trace Summary 重建读取失败，file={}, error={}",
                        segment.getFileName(),
                        safeMessage(exception));
            }
        }
        return found;
    }

    private ExecutionTraceRecord parseRecord(String line, Path segment) {
        try {
            return JSONUtils.parseObject(line, ExecutionTraceRecord.class);
        } catch (RuntimeException exception) {
            LOG.warn("Runtime Trace JSONL 记录解析失败，file={}, error={}", segment.getFileName(), safeMessage(exception));
            return null;
        }
    }

    private List<Path> segmentFiles(Path directory) {
        if (!Files.isDirectory(directory)) return List.of();
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> isSegmentFile(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            LOG.warn("Runtime Trace 分片目录读取失败，directory={}, error={}", directory.getFileName(), safeMessage(exception));
            return List.of();
        }
    }

    private boolean isSegmentFile(String fileName) {
        return fileName != null
                && fileName.length() == "trace-000001.jsonl".length()
                && fileName.startsWith("trace-")
                && fileName.endsWith(".jsonl");
    }

    private void writeSummary(Path directory, ExecutionTraceSummarySnapshot summary) {
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(SUMMARY_FILE);
            Path temporary = directory.resolve(SUMMARY_FILE + ".tmp");
            Files.writeString(
                    temporary,
                    JSONUtils.toJson(summary),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            LOG.warn("Runtime Trace Summary 写入失败，error={}", safeMessage(exception));
        }
    }

    private Path attemptDirectory(TraceKey key) {
        Path directory = rootDirectory
                .resolve(key.workspaceId())
                .resolve(key.executionId())
                .resolve("attempt-" + key.attemptNo())
                .normalize();
        if (!directory.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("execution trace directory escaped configured root");
        }
        return directory;
    }

    private TraceKey traceKey(String workspaceId, String executionId, int attemptNo) {
        if (attemptNo <= 0) throw new IllegalArgumentException("attemptNo must be greater than 0");
        return new TraceKey(
                requireSegment(workspaceId, "workspaceId"), requireSegment(executionId, "executionId"), attemptNo);
    }

    private String requireSegment(String value, String field) {
        String segment = StringUtils.trimToNull(value);
        if (segment == null) throw new IllegalArgumentException(field + " must not be blank");
        if (segment.contains("/") || segment.contains("\\") || ".".equals(segment) || "..".equals(segment)) {
            throw new IllegalArgumentException(field + " contains invalid path characters");
        }
        return segment;
    }

    private ExecutionTraceSummarySnapshot emptySummary(int attemptNo) {
        return ExecutionTraceSummarySnapshot.empty(attemptNo);
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) return null;
        String message = StringUtils.trimToNull(throwable.getMessage());
        if (message == null) message = throwable.getClass().getSimpleName();
        return safeMessage(message);
    }

    private String safeMessage(String message) {
        String value = StringUtils.trimToNull(SensitiveUtils.mask(message));
        if (value == null) return null;
        return value.length() > MAX_ERROR_MESSAGE_LENGTH ? value.substring(0, MAX_ERROR_MESSAGE_LENGTH) : value;
    }

    private static Path resolveRootDirectory() {
        String home = System.getProperty(HOME_PROPERTY);
        Path base = home == null || home.isBlank() ? Path.of(".") : Path.of(home);
        return base.resolve(TRACE_ROOT_DIRECTORY);
    }

    private final class FileTraceSession implements ExecutionTraceSession {

        private final TraceKey key;
        private final String attemptId;
        private final Path directory;
        private final ArrayBlockingQueue<ExecutionTraceRecord> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        private final Map<String, String> sourceSqlBySplit = new ConcurrentHashMap<>();
        private final TraceSummaryAccumulator summary;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean writerFailed = new AtomicBoolean();
        private final Thread writerThread;
        private final RuntimeTraceListener listener = this::accept;

        private FileTraceSession(TraceKey key, String attemptId, Path directory) {
            this.key = key;
            this.attemptId = attemptId;
            this.directory = directory;
            this.summary = new TraceSummaryAccumulator(key.attemptNo());
            this.writerThread = Thread.ofVirtual()
                    .name("yak-trace-" + key.executionId() + "-attempt-" + key.attemptNo())
                    .start(this::writeLoop);
        }

        @Override
        public RuntimeTraceListener listener() {
            return listener;
        }

        private void accept(RuntimeTraceEvent event) {
            if (event == null || closed.get()) return;
            ExecutionTraceRecord record;
            try {
                record = toRecord(event, attemptId, key.attemptNo(), sourceSqlBySplit);
            } catch (RuntimeException exception) {
                summary.addDropped(1L);
                return;
            }
            if (record == null) return;
            summary.accept(record);
            if (writerFailed.get() || !queue.offer(record)) {
                summary.addDropped(1L);
            }
        }

        private void writeLoop() {
            try (SegmentWriter writer = new SegmentWriter(directory)) {
                while (!closed.get() || !queue.isEmpty()) {
                    ExecutionTraceRecord record = queue.poll(100L, TimeUnit.MILLISECONDS);
                    if (record == null) continue;
                    writer.write(record);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                summary.addDropped(queue.size());
                queue.clear();
            } catch (Exception exception) {
                writerFailed.set(true);
                summary.addDropped(queue.size() + 1L);
                queue.clear();
                LOG.warn(
                        "Runtime Trace 异步写入失败，workspaceId={}, executionId={}, attempt={}, error={}",
                        key.workspaceId(),
                        key.executionId(),
                        key.attemptNo(),
                        safeMessage(exception));
            }
        }

        private ExecutionTraceSummarySnapshot summary(boolean complete) {
            return summary.snapshot(complete);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            try {
                writerThread.join(WRITER_JOIN_TIMEOUT_MILLIS);
                if (writerThread.isAlive()) {
                    summary.addDropped(queue.size());
                    queue.clear();
                    writerThread.interrupt();
                    writerThread.join(1000L);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                writeSummary(directory, summary(true));
                activeSessions.remove(key, this);
            }
        }
    }

    private static final class TraceSummaryAccumulator {

        private final int attemptNo;
        private long sourceSplitCount;
        private long sourceFinishedSplitCount;
        private long sourceFailedSplitCount;
        private long sourceRows;
        private long sourceSplitDurationMillis;
        private String sinkSql;
        private Integer sinkBatchSize;
        private String sinkSaveMode;
        private String sinkWriteMode;
        private long sinkCommittedBatchCount;
        private long sinkFailedBatchCount;
        private long sinkRows;
        private long sinkExecuteDurationMillis;
        private long sinkCommitDurationMillis;
        private long errorCount;
        private long droppedEventCount;

        private TraceSummaryAccumulator(int attemptNo) {
            this.attemptNo = attemptNo;
        }

        private synchronized void accept(ExecutionTraceRecord record) {
            if (JdbcTraceEventType.SOURCE_SPLIT_PLANNED.name().equals(record.type())) {
                sourceSplitCount++;
                return;
            }
            if (JdbcTraceEventType.SOURCE_SPLIT_FINISHED.name().equals(record.type())) {
                sourceFinishedSplitCount++;
                sourceRows += value(record.rows());
                sourceSplitDurationMillis += value(record.durationMillis());
                return;
            }
            if (JdbcTraceEventType.SOURCE_SPLIT_FAILED.name().equals(record.type())) {
                sourceFailedSplitCount++;
                sourceRows += value(record.rows());
                sourceSplitDurationMillis += value(record.durationMillis());
                errorCount++;
                return;
            }
            if (JdbcTraceEventType.SINK_OPENED.name().equals(record.type())) {
                sinkSql = record.sql();
                sinkBatchSize = record.batchSize();
                sinkSaveMode = record.saveMode();
                sinkWriteMode = record.writeMode();
                return;
            }
            if (JdbcTraceEventType.SINK_BATCH_COMMITTED.name().equals(record.type())) {
                sinkCommittedBatchCount++;
                sinkRows += value(record.rows());
                sinkExecuteDurationMillis += value(record.executeDurationMillis());
                sinkCommitDurationMillis += value(record.commitDurationMillis());
                return;
            }
            if (JdbcTraceEventType.SINK_BATCH_FAILED.name().equals(record.type())) {
                sinkFailedBatchCount++;
                sinkExecuteDurationMillis += value(record.executeDurationMillis());
                sinkCommitDurationMillis += value(record.commitDurationMillis());
                errorCount++;
            }
        }

        private synchronized void addDropped(long count) {
            droppedEventCount += Math.max(0L, count);
        }

        private synchronized ExecutionTraceSummarySnapshot snapshot(boolean complete) {
            return new ExecutionTraceSummarySnapshot(
                    attemptNo,
                    true,
                    complete,
                    sourceSplitCount,
                    sourceFinishedSplitCount,
                    sourceFailedSplitCount,
                    sourceRows,
                    sourceSplitDurationMillis,
                    sinkSql,
                    sinkBatchSize,
                    sinkSaveMode,
                    sinkWriteMode,
                    sinkCommittedBatchCount,
                    sinkFailedBatchCount,
                    sinkRows,
                    sinkExecuteDurationMillis,
                    sinkCommitDurationMillis,
                    errorCount,
                    droppedEventCount);
        }

        private long value(Long value) {
            return value == null ? 0L : value;
        }
    }

    private final class SegmentWriter implements AutoCloseable {

        private final Path directory;
        private BufferedWriter writer;
        private long segmentBytes;
        private int segmentNumber;

        private SegmentWriter(Path directory) throws IOException {
            this.directory = directory;
            Files.createDirectories(directory);
            this.segmentNumber = nextSegmentNumber(directory);
            openSegment();
        }

        private void write(ExecutionTraceRecord record) throws IOException {
            String line = JSONUtils.toJson(record);
            long lineBytes = line.getBytes(StandardCharsets.UTF_8).length + 1L;
            if (segmentBytes > 0 && segmentBytes + lineBytes > MAX_SEGMENT_BYTES) {
                writer.close();
                segmentNumber++;
                openSegment();
            }
            writer.write(line);
            writer.newLine();
            writer.flush();
            segmentBytes += lineBytes;
        }

        private void openSegment() throws IOException {
            Path file = directory.resolve(String.format("trace-%06d.jsonl", segmentNumber));
            writer = Files.newBufferedWriter(
                    file,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE);
            segmentBytes = Files.size(file);
        }

        @Override
        public void close() throws IOException {
            if (writer != null) writer.close();
        }
    }

    private int nextSegmentNumber(Path directory) {
        int max = 0;
        for (Path path : segmentFiles(directory)) {
            String fileName = path.getFileName().toString();
            try {
                max = Math.max(max, Integer.parseInt(fileName.substring(6, 12)));
            } catch (RuntimeException ignored) {
                // segmentFiles 已过滤标准文件名；保留防御性处理。
            }
        }
        return max + 1;
    }

    private record TraceKey(String workspaceId, String executionId, int attemptNo) {}

    private record TraceCursor(int segmentIndex, int lineIndex) {

        private static TraceCursor parse(String value) {
            String cursor = StringUtils.trimToNull(value);
            if (cursor == null) return new TraceCursor(0, 0);
            String[] parts = cursor.split(":", -1);
            if (parts.length != 2) throw new IllegalArgumentException("invalid trace cursor");
            try {
                int segment = Integer.parseInt(parts[0]);
                int line = Integer.parseInt(parts[1]);
                if (segment < 0 || line < 0) throw new IllegalArgumentException("invalid trace cursor");
                return new TraceCursor(segment, line);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid trace cursor", exception);
            }
        }

        private static String encode(int segmentIndex, int lineIndex) {
            return segmentIndex + ":" + lineIndex;
        }
    }
}
