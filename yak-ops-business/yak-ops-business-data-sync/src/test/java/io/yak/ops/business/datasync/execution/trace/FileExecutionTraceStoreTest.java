package io.yak.ops.business.datasync.execution.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.flow.connector.jdbc.trace.JdbcSinkBatchTraceEvent;
import io.yak.ops.flow.connector.jdbc.trace.JdbcSinkOpenedTraceEvent;
import io.yak.ops.flow.connector.jdbc.trace.JdbcSourceSplitTraceEvent;
import io.yak.ops.flow.connector.jdbc.trace.JdbcTraceEventType;
import io.yak.ops.flow.connector.jdbc.trace.JdbcTraceFailureStage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileExecutionTraceStoreTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldPersistAttemptSummaryAndCursorPages() throws Exception {
        FileExecutionTraceStore store = new FileExecutionTraceStore(tempDirectory);
        ExecutionTraceSession session = store.openSession("workspace-1", "execution-1", "attempt-1", 1);

        session.listener().emit(new JdbcSourceSplitTraceEvent(
                Instant.parse("2026-10-03T12:00:00Z"),
                JdbcTraceEventType.SOURCE_SPLIT_PLANNED,
                "source#id[1,10]",
                null,
                "SELECT id, name FROM source WHERE id >= ? AND id <= ?",
                List.of(1L, 10L),
                "id",
                1L,
                10L,
                0L,
                0L,
                null,
                null,
                null));
        session.listener().emit(new JdbcSourceSplitTraceEvent(
                Instant.parse("2026-10-03T12:00:01Z"),
                JdbcTraceEventType.SOURCE_SPLIT_FINISHED,
                "source#id[1,10]",
                "yak-flow-source-reader-0",
                null,
                List.of(1L, 10L),
                "id",
                1L,
                10L,
                10L,
                120L,
                null,
                null,
                null));
        session.listener().emit(new JdbcSinkOpenedTraceEvent(
                Instant.parse("2026-10-03T12:00:01Z"),
                "INSERT INTO target (id, name) VALUES (?, ?)",
                5,
                "APPEND",
                "INSERT"));
        session.listener().emit(new JdbcSinkBatchTraceEvent(
                Instant.parse("2026-10-03T12:00:02Z"),
                JdbcTraceEventType.SINK_BATCH_COMMITTED,
                1L,
                5L,
                30L,
                5L,
                null,
                null,
                null));
        session.listener().emit(new JdbcSinkBatchTraceEvent(
                Instant.parse("2026-10-03T12:00:03Z"),
                JdbcTraceEventType.SINK_BATCH_COMMITTED,
                2L,
                5L,
                25L,
                4L,
                null,
                null,
                null));

        ExecutionTraceSummarySnapshot active = store.querySummary("workspace-1", "execution-1", 1);
        assertTrue(active.available());
        assertFalse(active.complete());
        assertEquals(1L, active.sourceSplitCount());
        assertEquals(1L, active.sourceFinishedSplitCount());
        assertEquals(10L, active.sourceRows());
        assertEquals(2L, active.sinkCommittedBatchCount());
        assertEquals(10L, active.sinkRows());

        session.close();

        ExecutionTraceSummarySnapshot completed = store.querySummary("workspace-1", "execution-1", 1);
        assertTrue(completed.available());
        assertTrue(completed.complete());
        assertEquals("INSERT INTO target (id, name) VALUES (?, ?)", completed.sinkSql());
        assertEquals(5, completed.sinkBatchSize());
        assertEquals(55L, completed.sinkExecuteDurationMillis());
        assertEquals(9L, completed.sinkCommitDurationMillis());

        ExecutionTracePage sourcePage =
                store.queryPage("workspace-1", "execution-1", 1, ExecutionTraceSide.SOURCE, 50, null, null);
        assertEquals(1, sourcePage.records().size());
        assertEquals("SELECT id, name FROM source WHERE id >= ? AND id <= ?", sourcePage.records().get(0).sql());
        assertFalse(sourcePage.hasMore());

        ExecutionTracePage firstSinkPage =
                store.queryPage("workspace-1", "execution-1", 1, ExecutionTraceSide.SINK, 1, null, "SUCCESS");
        assertEquals(1, firstSinkPage.records().size());
        assertTrue(firstSinkPage.hasMore());
        assertNotNull(firstSinkPage.nextCursor());

        ExecutionTracePage secondSinkPage = store.queryPage(
                "workspace-1",
                "execution-1",
                1,
                ExecutionTraceSide.SINK,
                1,
                firstSinkPage.nextCursor(),
                "SUCCESS");
        assertEquals(1, secondSinkPage.records().size());
        assertEquals(2L, secondSinkPage.records().get(0).batchNo());
        assertFalse(secondSinkPage.hasMore());

        Path attemptDirectory = tempDirectory.resolve("workspace-1").resolve("execution-1").resolve("attempt-1");
        assertTrue(Files.isRegularFile(attemptDirectory.resolve("summary.json")));
        assertTrue(Files.isRegularFile(attemptDirectory.resolve("trace-000001.jsonl")));
    }

    @Test
    void shouldPersistFailureContextAndFilterByStatus() {
        FileExecutionTraceStore store = new FileExecutionTraceStore(tempDirectory);
        ExecutionTraceSession session = store.openSession("workspace-1", "execution-2", "attempt-2", 2);

        session.listener().emit(new JdbcSourceSplitTraceEvent(
                Instant.now(),
                JdbcTraceEventType.SOURCE_SPLIT_PLANNED,
                "source#id[11,20]",
                null,
                "SELECT id FROM source WHERE id >= ? AND id <= ?",
                List.of(11L, 20L),
                "id",
                11L,
                20L,
                0L,
                0L,
                null,
                null,
                null));
        session.listener().emit(new JdbcSourceSplitTraceEvent(
                Instant.now(),
                JdbcTraceEventType.SOURCE_SPLIT_FAILED,
                "source#id[11,20]",
                "yak-flow-source-reader-1",
                null,
                List.of(11L, 20L),
                "id",
                11L,
                20L,
                3L,
                87L,
                JdbcTraceFailureStage.SOURCE_READ,
                "java.sql.SQLException",
                "password=secret connection failed"));
        session.listener().emit(new JdbcSinkBatchTraceEvent(
                Instant.now(),
                JdbcTraceEventType.SINK_BATCH_FAILED,
                1L,
                3L,
                10L,
                0L,
                JdbcTraceFailureStage.SINK_WRITE,
                "java.sql.BatchUpdateException",
                "password=secret duplicate key"));

        session.close();

        ExecutionTraceSummarySnapshot summary = store.querySummary("workspace-1", "execution-2", 2);
        assertEquals(1L, summary.sourceFailedSplitCount());
        assertEquals(1L, summary.sinkFailedBatchCount());
        assertEquals(2L, summary.errorCount());

        ExecutionTracePage sourceFailed =
                store.queryPage("workspace-1", "execution-2", 2, ExecutionTraceSide.SOURCE, 50, null, "FAILED");
        assertEquals(1, sourceFailed.records().size());
        assertFalse(sourceFailed.records().get(0).errorMessage().contains("secret"));

        ExecutionTracePage sourceSuccess =
                store.queryPage("workspace-1", "execution-2", 2, ExecutionTraceSide.SOURCE, 50, null, "SUCCESS");
        assertTrue(sourceSuccess.records().isEmpty());

        ExecutionTracePage sinkFailed =
                store.queryPage("workspace-1", "execution-2", 2, ExecutionTraceSide.SINK, 50, null, "FAILED");
        assertEquals(1, sinkFailed.records().size());
        assertEquals("SINK_WRITE", sinkFailed.records().get(0).failureStage());
    }

    @Test
    void shouldReturnUnavailableSummaryWhenTraceDoesNotExist() {
        FileExecutionTraceStore store = new FileExecutionTraceStore(tempDirectory);

        ExecutionTraceSummarySnapshot summary = store.querySummary("workspace-1", "missing-execution", 1);

        assertFalse(summary.available());
        assertFalse(summary.complete());
        assertEquals(0L, summary.sourceSplitCount());
        assertEquals(0L, summary.sinkCommittedBatchCount());
    }
}
