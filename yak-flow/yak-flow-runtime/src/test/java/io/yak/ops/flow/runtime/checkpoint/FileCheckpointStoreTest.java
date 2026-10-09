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
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileCheckpointStoreTest {

    @TempDir
    Path folder;

    @Test
    void shouldCommitAndRestoreImmutableVersionedState() throws Exception {
        String signature = FileCheckpointStore.graphSignature(graph(1));
        byte[] values = {3, 4, 5};
        var state = new CheckpointSnapshot.SerializedState(2, values);
        var checkpoint = new CheckpointSnapshot(
                7, signature, state, Map.of(0, List.of(state)), Map.of(), 123456L);
        values[0] = 100;

        try (FileCheckpointStore store = new FileCheckpointStore(folder)) {
            assertTrue(store.loadLatest(signature).isEmpty());
            store.save(checkpoint);
            CheckpointSnapshot loaded = store.loadLatest(signature).orElseThrow();
            assertEquals(7, loaded.checkpointId());
            assertEquals(2, loaded.enumeratorState().version());
            assertArrayEquals(new byte[] {3, 4, 5}, loaded.enumeratorState().bytes());
            loaded.enumeratorState().bytes()[0] = 99;
            assertArrayEquals(new byte[] {3, 4, 5}, loaded.enumeratorState().bytes());
            assertEquals(1, loaded.readerSplits().get(0).size());
            assertThrows(IllegalStateException.class, () -> new FileCheckpointStore(folder));
            assertThrows(IllegalStateException.class, () -> store.loadLatest("wrong-fingerprint"));
        }
        try (FileCheckpointStore reopened = new FileCheckpointStore(folder)) {
            assertEquals(7, reopened.loadLatest(signature).orElseThrow().checkpointId());
        }
    }

    @Test
    void shouldRejectCorruptedSnapshotWithoutSilentlyIgnoringIt() throws Exception {
        String signature = FileCheckpointStore.graphSignature(graph(1));
        var state = new CheckpointSnapshot.SerializedState(1, new byte[] {1, 2, 3});
        try (FileCheckpointStore store = new FileCheckpointStore(folder)) {
            store.save(new CheckpointSnapshot(1, signature, state, Map.of(), Map.of(), 100L));
            byte[] corrupted = Files.readAllBytes(folder.resolve("checkpoint.bin"));
            corrupted[corrupted.length / 2] ^= 0x3f;
            Files.write(folder.resolve("checkpoint.bin"), corrupted);
            assertThrows(IOException.class, () -> store.loadLatest(signature));
        }
    }

    @Test
    void shouldRequireStableUidAndRejectDifferentParallelism() throws Exception {
        StreamGraph graph = graph(1);
        String signature = FileCheckpointStore.graphSignature(graph);
        assertFalse(signature.isBlank());
        assertFalse(signature.equals(FileCheckpointStore.graphSignature(graph(2))));
        SourceTransformation<String> source = new SourceTransformation<>("source", new NoRuntimeSource(), String.class);
        Sink<String> sink = () -> {
            throw new AssertionError("Graph generation must not start Writer");
        };
        StreamGraph withoutUids = new StreamGraphGenerator(
                new SinkTransformation<>(source, "sink", sink), new Configuration()).generate();
        assertThrows(IllegalArgumentException.class, () -> FileCheckpointStore.graphSignature(withoutUids));
    }

    @Test
    void shouldDurablyPersistOperatorStateWithoutBreakingLegacyFormat() throws Exception {
        String signature = FileCheckpointStore.graphSignature(graph(1));
        var encoded = new CheckpointSnapshot.SerializedState(3, new byte[]{7, 9, 11});
        var key = new CheckpointSnapshot.OperatorSubtask("stable-operator", 0);
        var complete = new CheckpointSnapshot(5, signature, encoded, Map.of(), Map.of(),
                123456L, Map.of(key, Map.of("operator/count", encoded)));
        try (FileCheckpointStore store = new FileCheckpointStore(folder)) {
            store.save(complete);
            var recovered = store.loadLatest(signature).orElseThrow();
            assertEquals(3, recovered.operatorStates().get(key).get("operator/count").version());
            assertArrayEquals(new byte[]{7, 9, 11},
                    recovered.operatorStates().get(key).get("operator/count").bytes());
            recovered.operatorStates().get(key).get("operator/count").bytes()[0] = 50;
            assertArrayEquals(new byte[]{7, 9, 11},
                    store.loadLatest(signature).orElseThrow().operatorStates()
                            .get(key).get("operator/count").bytes());
        }
    }

    private static StreamGraph graph(int sourceParallelism) {
        SourceTransformation<String> source =
                new SourceTransformation<>("source", new NoRuntimeSource(), String.class, sourceParallelism);
        Sink<String> sink = () -> {
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
