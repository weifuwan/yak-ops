package io.yak.ops.flow.runtime.checkpoint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.configuration.Configuration;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCompletedCheckpointStoreTest {

    @TempDir
    Path folder;

    @Test
    void shouldCommitAndRestoreImmutableVersionedState() throws Exception {
        String signature = LocalCompletedCheckpointStore.graphSignature(graph(1));
        byte[] values = {3, 4, 5};
        var state = new CheckpointSnapshot.SerializedState(2, values);
        var checkpoint = new CheckpointSnapshot(
                7, signature, state, Map.of(0, List.of(state)), Map.of(), 123456L);
        values[0] = 100;

        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(folder)) {
            assertTrue(store.loadLatest(signature).isEmpty());
            store.save(checkpoint);
            CheckpointSnapshot loaded = store.loadLatest(signature).orElseThrow();
            assertEquals(checkpoint, loaded);
            assertEquals(7, loaded.checkpointId());
            assertTrue(loaded.fullyFinishedOperators().isEmpty());
            assertTrue(loaded.finishedSubtasks().isEmpty());
            assertEquals(2, ByteBuffer.wrap(Files.readAllBytes(folder.resolve("completed/chk-7"))).getInt(4));
            assertEquals(2, ByteBuffer.wrap(Files.readAllBytes(
                    folder.resolve("checkpoints/chk-7/_metadata"))).getInt(4));
            assertEquals(2, loaded.enumeratorState().version());
            assertArrayEquals(new byte[] {3, 4, 5}, loaded.enumeratorState().bytes());
            loaded.enumeratorState().bytes()[0] = 99;
            assertArrayEquals(new byte[] {3, 4, 5}, loaded.enumeratorState().bytes());
            assertEquals(1, loaded.readerSplits().get(0).size());
            assertThrows(IllegalStateException.class, () -> new LocalCompletedCheckpointStore(folder));
            assertThrows(IllegalStateException.class, () -> store.loadLatest("wrong-fingerprint"));
        }
        try (LocalCompletedCheckpointStore reopened = new LocalCompletedCheckpointStore(folder)) {
            assertEquals(7, reopened.loadLatest(signature).orElseThrow().checkpointId());
        }
    }

    @Test
    void shouldRejectRestoreFromDifferentTaskDefinitionDespiteIdenticalOperatorTopology() throws Exception {
        StreamGraph sameGraph = graph(1);
        String original = LocalCompletedCheckpointStore.graphSignature(sameGraph, 128, "workspace:definition:1:instance");
        String changed = LocalCompletedCheckpointStore.graphSignature(sameGraph, 128, "workspace:definition:2:instance");
        String anotherInstance = LocalCompletedCheckpointStore.graphSignature(
                sameGraph, 128, "workspace:definition:1:another-instance");
        assertFalse(original.equals(changed));
        assertFalse(original.equals(anotherInstance));
        var state = new CheckpointSnapshot.SerializedState(1, new byte[] {1});
        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(folder)) {
            store.save(new CheckpointSnapshot(1, original, state, Map.of(), Map.of(), 100L));
            assertThrows(IllegalStateException.class, () -> store.loadLatest(changed));
            assertThrows(IllegalStateException.class, () -> store.loadLatest(anotherInstance));
            assertEquals(original, store.loadLatest(original).orElseThrow().graphSignature());
        }
    }

    @Test
    void shouldRejectCorruptedSnapshotWithoutSilentlyIgnoringIt() throws Exception {
        String signature = LocalCompletedCheckpointStore.graphSignature(graph(1));
        var state = new CheckpointSnapshot.SerializedState(1, new byte[] {1, 2, 3});
        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(folder)) {
            store.save(new CheckpointSnapshot(1, signature, state, Map.of(), Map.of(), 100L));
            byte[] corrupted = Files.readAllBytes(folder.resolve("completed/chk-1"));
            corrupted[corrupted.length / 2] ^= 0x3f;
            Files.write(folder.resolve("completed/chk-1"), corrupted);
            assertThrows(IOException.class, () -> store.loadLatest(signature));
        }
    }

    @Test
    void shouldRejectLegacyCheckpointVersionEvenWithValidCrc() throws Exception {
        String signature = LocalCompletedCheckpointStore.graphSignature(graph(1));
        var state = new CheckpointSnapshot.SerializedState(1, new byte[] {1, 2, 3});
        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(folder)) {
            store.save(new CheckpointSnapshot(1, signature, state, Map.of(), Map.of(), 100L));
            byte[] file = Files.readAllBytes(folder.resolve("completed/chk-1"));
            ByteBuffer.wrap(file).putInt(4, 1);
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(file, 0, file.length - Long.BYTES);
            ByteBuffer.wrap(file).putLong(file.length - Long.BYTES, crc.getValue());
            Files.write(folder.resolve("completed/chk-1"), file);
            assertThrows(IOException.class, () -> store.loadLatest(signature));
        }
        try (LocalCompletedCheckpointStore reopened = new LocalCompletedCheckpointStore(folder)) {
            var next = new CheckpointSnapshot(2, signature, state, Map.of(), Map.of(), 200L);
            assertThrows(IOException.class, () -> reopened.save(next));
        }
    }

    @Test
    void shouldRejectMissingOrCorruptPublishedMetadataWithoutFallingBack() throws Exception {
        String signature = LocalCompletedCheckpointStore.graphSignature(graph(1));
        var state = new CheckpointSnapshot.SerializedState(1, new byte[] {1, 2, 3});
        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(folder)) {
            store.save(new CheckpointSnapshot(1, signature, state, Map.of(), Map.of(), 100L));
            Path metadata = folder.resolve("checkpoints/chk-1/_metadata");
            byte[] file = Files.readAllBytes(metadata);
            file[file.length - Long.BYTES - 1] ^= 0x0f;
            Files.write(metadata, file);
            assertThrows(IOException.class, () -> store.loadLatest(signature));
        }
        Files.delete(folder.resolve("checkpoints/chk-1/_metadata"));
        try (LocalCompletedCheckpointStore reopened = new LocalCompletedCheckpointStore(folder)) {
            assertThrows(IOException.class, () -> reopened.loadLatest(signature));
        }
    }

    @Test
    void shouldNotOverwriteLatestCheckpointWithSameOrOlderId() throws Exception {
        String signature = LocalCompletedCheckpointStore.graphSignature(graph(1));
        var state = new CheckpointSnapshot.SerializedState(1, new byte[] {7});
        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(folder)) {
            var latest = new CheckpointSnapshot(7, signature, state, Map.of(), Map.of(), 700L);
            store.save(latest);
            assertThrows(IOException.class, () -> store.save(latest));
            assertThrows(IOException.class, () -> store.save(
                    new CheckpointSnapshot(6, signature, state, Map.of(), Map.of(), 600L)));
            assertEquals(latest, store.loadLatest(signature).orElseThrow());
        }
    }

    @Test
    void keepsLatestTwoCompletedCheckpointsAndDiscardsSubsumedMetadata() throws Exception {
        String signature = LocalCompletedCheckpointStore.graphSignature(graph(1));
        var state = new CheckpointSnapshot.SerializedState(1, new byte[] {1});
        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(folder)) {
            for (long id = 1; id <= 3; id++) {
                store.save(new CheckpointSnapshot(id, signature, state, Map.of(), Map.of(), 100L + id));
            }
            assertFalse(Files.exists(folder.resolve("checkpoints/chk-1/_metadata")));
            assertTrue(Files.isRegularFile(folder.resolve("checkpoints/chk-2/_metadata")));
            assertTrue(Files.isRegularFile(folder.resolve("checkpoints/chk-3/_metadata")));
            assertFalse(Files.exists(folder.resolve("completed/chk-1")));
            assertTrue(Files.exists(folder.resolve("completed/chk-2")));
            assertTrue(Files.exists(folder.resolve("completed/chk-3")));
            assertEquals(3, store.loadLatest(signature).orElseThrow().checkpointId());
        }
    }

    @Test
    void shouldRequireStableUidAndRejectDifferentParallelism() throws Exception {
        StreamGraph graph = graph(1);
        String signature = LocalCompletedCheckpointStore.graphSignature(graph);
        assertFalse(signature.isBlank());
        assertFalse(signature.equals(LocalCompletedCheckpointStore.graphSignature(graph(2))));
        SourceTransformation<String> source = new SourceTransformation<>("source", new NoRuntimeSource(), String.class);
        Sink<String> sink = context -> {
            throw new AssertionError("Graph generation must not start Writer");
        };
        StreamGraph withoutUids = new StreamGraphGenerator(
                new SinkTransformation<>(source, "sink", sink), new Configuration()).generate();
        assertThrows(IllegalArgumentException.class, () -> LocalCompletedCheckpointStore.graphSignature(withoutUids));
    }

    @Test
    void shouldDurablyPersistOperatorStateUsingUnifiedFormat() throws Exception {
        String signature = LocalCompletedCheckpointStore.graphSignature(graph(1));
        var encoded = new CheckpointSnapshot.SerializedState(3, new byte[]{7, 9, 11});
        var key = new CheckpointSnapshot.OperatorSubtask("stable-operator", 0);
        var complete = new CheckpointSnapshot(5, signature, encoded, Map.of(), Map.of(),
                123456L, Map.of(key, Map.of("operator/count", encoded)));
        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(folder)) {
            store.save(complete);
            var recovered = store.loadLatest(signature).orElseThrow();
            assertEquals(complete, recovered);
            assertEquals(2, ByteBuffer.wrap(Files.readAllBytes(folder.resolve("completed/chk-5"))).getInt(4));
            assertTrue(recovered.fullyFinishedOperators().isEmpty());
            assertTrue(recovered.finishedSubtasks().isEmpty());
            assertEquals(3, recovered.operatorStates().get(key).get("operator/count").version());
            assertArrayEquals(new byte[]{7, 9, 11},
                    recovered.operatorStates().get(key).get("operator/count").bytes());
            recovered.operatorStates().get(key).get("operator/count").bytes()[0] = 50;
            assertArrayEquals(new byte[]{7, 9, 11},
                    store.loadLatest(signature).orElseThrow().operatorStates()
                            .get(key).get("operator/count").bytes());
        }
    }

    @Test
    void shouldPersistFinishedOperatorMarkersWithoutLosingActiveState() throws Exception {
        String signature = LocalCompletedCheckpointStore.graphSignature(graph(1));
        var state = new CheckpointSnapshot.SerializedState(4, new byte[] {5, 6});
        var active = new CheckpointSnapshot.OperatorSubtask("running-operator", 0);
        var finished = new CheckpointSnapshot.OperatorSubtask("sink-stable-uid", 0);
        var snapshot = new CheckpointSnapshot(
                12,
                signature,
                state,
                Map.of(),
                Map.of(),
                123456L,
                Map.of(active, Map.of("operator/pending", state)),
                Set.of("source-stable-uid"),
                Set.of(finished));
        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(folder)) {
            store.save(snapshot);
            assertEquals(2, ByteBuffer.wrap(Files.readAllBytes(folder.resolve("completed/chk-12"))).getInt(4));
            CheckpointSnapshot recovered = store.loadLatest(signature).orElseThrow();
            assertEquals(snapshot, recovered);
            assertEquals(Set.of("source-stable-uid"), recovered.fullyFinishedOperators());
            assertEquals(Set.of(finished), recovered.finishedSubtasks());
            assertEquals(4, recovered.operatorStates().get(active).get("operator/pending").version());
        }
    }

    @Test
    void shouldRejectFinishedMarkersConflictingWithLiveOperatorState() {
        var state = new CheckpointSnapshot.SerializedState(1, new byte[] {1});
        var subtask = new CheckpointSnapshot.OperatorSubtask("source-stable-uid", 0);
        String signature = LocalCompletedCheckpointStore.graphSignature(graph(1));
        assertThrows(IllegalArgumentException.class,
                () -> new CheckpointSnapshot(2, signature, state, Map.of(), Map.of(), 10L,
                        Map.of(subtask, Map.of("count", state)), Set.of("source-stable-uid"), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new CheckpointSnapshot(2, signature, state, Map.of(), Map.of(), 10L,
                        Map.of(subtask, Map.of("count", state)), Set.of(), Set.of(subtask)));
        assertThrows(IllegalArgumentException.class,
                () -> new CheckpointSnapshot(2, signature, state, Map.of(), Map.of(), 10L,
                        Map.of(), Set.of("source-stable-uid"), Set.of(subtask)));
    }

    @Test
    void shouldIgnoreMetadataWithoutCompletedMarkerAndRejectLegacyState() throws Exception {
        String signature = LocalCompletedCheckpointStore.graphSignature(graph(1));
        CheckpointSnapshot.SerializedState state = new CheckpointSnapshot.SerializedState(1, new byte[] {1});
        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(folder)) {
            store.save(new CheckpointSnapshot(1, signature, state, Map.of(), Map.of(), 100L));
        }
        Files.delete(folder.resolve("completed/chk-1"));
        try (LocalCompletedCheckpointStore reopened = new LocalCompletedCheckpointStore(folder)) {
            assertTrue(reopened.loadLatest(signature).isEmpty());
            assertTrue(LocalCompletedCheckpointStore.hasCheckpointData(folder));
        }
        Files.write(folder.resolve("checkpoint.bin"), new byte[] {1});
        try (LocalCompletedCheckpointStore reopened = new LocalCompletedCheckpointStore(folder)) {
            assertThrows(IOException.class, () -> reopened.loadLatest(signature));
        }
    }

    private static StreamGraph graph(int sourceParallelism) {
        SourceTransformation<String> source =
                new SourceTransformation<>("source", new NoRuntimeSource(), String.class, sourceParallelism);
        Sink<String> sink = context -> {
            throw new AssertionError("Graph generation must not start Writer");
        };
        SinkTransformation<String> target = new SinkTransformation<>(source, "sink", sink);
        source.setUid("source-stable-uid");
        target.setUid("sink-stable-uid");
        return new StreamGraphGenerator(target, new Configuration()).generate();
    }

    private record Split(String splitId) implements SourceSplit {}

    private static final class NoRuntimeSource implements Source<String, Split, Integer> {
        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }

        @Override
        public SplitEnumerator<Split, Integer> createEnumerator(SplitEnumeratorContext<Split> context) {
            throw new AssertionError();
        }

        @Override
        public SplitEnumerator<Split, Integer> restoreEnumerator(SplitEnumeratorContext<Split> context,
                Integer state) {
            throw new AssertionError();
        }

        @Override
        public SourceReader<String, Split> createReader(SourceReaderContext context) {
            throw new AssertionError();
        }

        @Override
        public SimpleVersionedSerializer<Split> getSplitSerializer() {
            throw new AssertionError();
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            throw new AssertionError();
        }
    }
}
