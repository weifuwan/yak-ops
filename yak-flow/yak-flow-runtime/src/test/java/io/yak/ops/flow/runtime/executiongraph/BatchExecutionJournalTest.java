package io.yak.ops.flow.runtime.executiongraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.api.connector.sink.BatchExecutionHook;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.graph.StreamingJobGraphGenerator;
import io.yak.ops.flow.runtime.jobgraph.JobGraph;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Cross-platform batch phase publication, crash recovery and legacy state migration. */
class BatchExecutionJournalTest {

    @TempDir Path directory;

    @Test
    void writesImmutableVersionsAndRestoresWithoutReplayingCommittedHook() throws Exception {
        RecordingHook hook = new RecordingHook();
        try (BatchExecutionJournal journal = new BatchExecutionJournal(graph(false), List.of(hook))) {
            assertFalse(journal.dataStarted());
            journal.execute(BatchExecutionHook.Phase.BEFORE_DATA, () -> false, ignored -> {});
            journal.beginData();
            journal.completeData();
            assertTrue(journal.dataCompleted());
        }

        assertTrue(BatchExecutionJournal.hasExecutionState(directory));
        assertEquals(2, versions());
        assertEquals(1, hook.executions.get());
        try (BatchExecutionJournal restored = new BatchExecutionJournal(graph(true), List.of(hook))) {
            assertTrue(restored.dataCompleted());
            restored.execute(BatchExecutionHook.Phase.BEFORE_DATA, () -> false, ignored -> {});
        }
        assertEquals(1, hook.executions.get());
    }

    @Test
    void discardsUncommittedFlinkStagingFilesBeforeStartingFresh() throws Exception {
        Path versions = Files.createDirectories(directory.resolve("batch-execution"));
        Path orphan = versions.resolve(
                ".journal-00000000000000000001.bin.inprogress.00000000-0000-0000-0000-000000000001");
        Files.write(orphan, new byte[] {1, 2, 3});
        assertFalse(BatchExecutionJournal.hasExecutionState(directory));

        try (BatchExecutionJournal journal = new BatchExecutionJournal(graph(false), List.of(new RecordingHook()))) {
            assertFalse(journal.dataStarted());
        }
        assertFalse(Files.exists(orphan));
        assertEquals(1, versions());
    }

    @Test
    void rejectsCorruptedLatestInsteadOfFallingBackToEarlierVersion() throws Exception {
        try (BatchExecutionJournal journal = new BatchExecutionJournal(graph(false), List.of(new RecordingHook()))) {
            journal.beginData();
        }
        assertEquals(2, versions());
        Files.write(
                directory.resolve("batch-execution/journal-00000000000000000002.bin"),
                new byte[] {1, 2, 3});

        assertTrue(BatchExecutionJournal.hasExecutionState(directory));
        assertThrows(
                IOException.class, () -> new BatchExecutionJournal(graph(true), List.of(new RecordingHook())));
    }

    @Test
    void migratesValidatedLegacyJournalWithoutRepeatingExternalOperation() throws Exception {
        RecordingHook hook = new RecordingHook();
        try (BatchExecutionJournal journal = new BatchExecutionJournal(graph(false), List.of(hook))) {
            journal.execute(BatchExecutionHook.Phase.BEFORE_DATA, () -> false, ignored -> {});
        }
        Path versions = directory.resolve("batch-execution");
        Path latest = versions.resolve("journal-00000000000000000003.bin");
        Path legacy = directory.resolve("batch-execution.bin");
        Files.copy(latest, legacy);
        try (Stream<Path> files = Files.list(versions)) {
            for (Path entry : files.toList()) {
                Files.delete(entry);
            }
        }
        Files.delete(versions);

        try (BatchExecutionJournal restored = new BatchExecutionJournal(graph(true), List.of(hook))) {
            restored.execute(BatchExecutionHook.Phase.BEFORE_DATA, () -> false, ignored -> {});
        }
        assertEquals(1, hook.executions.get());
        assertFalse(Files.exists(legacy));
        assertEquals(1, versions());
    }

    @Test
    void retainsExclusiveLockAndRequiresExplicitRecovery() throws Exception {
        RecordingHook hook = new RecordingHook();
        try (BatchExecutionJournal journal = new BatchExecutionJournal(graph(false), List.of(hook))) {
            assertThrows(IOException.class, () -> new BatchExecutionJournal(graph(true), List.of(hook)));
        }
        assertThrows(IOException.class, () -> new BatchExecutionJournal(graph(false), List.of(hook)));
        try (BatchExecutionJournal restored = new BatchExecutionJournal(graph(true), List.of(hook))) {
            assertFalse(restored.dataStarted());
        }
    }

    @Test
    void rejectsUnexpectedCommittedEntriesWithoutStartingFromScratch() throws Exception {
        Path versions = Files.createDirectories(directory.resolve("batch-execution"));
        Files.write(versions.resolve("journal-corrupt.bin"), new byte[] {1});
        assertTrue(BatchExecutionJournal.hasExecutionState(directory));
        assertThrows(
                IOException.class, () -> new BatchExecutionJournal(graph(true), List.of(new RecordingHook())));
    }

    private long versions() throws IOException {
        try (Stream<Path> files = Files.list(directory.resolve("batch-execution"))) {
            return files.filter(path -> path.getFileName().toString().startsWith("journal-")).count();
        }
    }

    private JobGraph graph(boolean restore) {
        Configuration config = new Configuration()
                .set(CheckpointingOptions.STATE_DIRECTORY, directory.toString())
                .set(CheckpointingOptions.DEFINITION_IDENTITY, "batch-test-definition")
                .set(CheckpointingOptions.RESTORE_LATEST, restore);
        SourceTransformation<String> source =
                new SourceTransformation<>("source", new NoRuntimeSource(), String.class, 1);
        Sink<String> sink = context -> {
            throw new AssertionError("Journal tests must not create Sink Writers");
        };
        SinkTransformation<String> target = new SinkTransformation<>(source, "sink", sink, 1);
        source.setUid("batch-source");
        target.setUid("batch-sink");
        return new StreamingJobGraphGenerator(new StreamGraphGenerator(target, config).generate(), config)
                .generate();
    }

    private static final class RecordingHook implements BatchExecutionHook {
        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public Phase phase() {
            return Phase.BEFORE_DATA;
        }

        @Override
        public String fingerprint() {
            return "a".repeat(64);
        }

        @Override
        public void execute() {
            executions.incrementAndGet();
        }
    }

    private static final class NoRuntimeSource implements Source<String, TestSplit, Integer> {
        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> createEnumerator(SplitEnumeratorContext<TestSplit> context) {
            throw new AssertionError("Journal test must not create enumerator");
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> restoreEnumerator(
                SplitEnumeratorContext<TestSplit> context, Integer state) {
            throw new AssertionError("Journal test must not restore enumerator");
        }

        @Override
        public SourceReader<String, TestSplit> createReader(SourceReaderContext context) {
            throw new AssertionError("Journal test must not create reader");
        }

        @Override
        public SimpleVersionedSerializer<TestSplit> getSplitSerializer() {
            throw new AssertionError("Journal test must not serialize source split");
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            throw new AssertionError("Journal test must not serialize enumerator");
        }
    }

    private static final class TestSplit implements SourceSplit {
        @Override
        public String splitId() {
            return "test";
        }
    }
}
