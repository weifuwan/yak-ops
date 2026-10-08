package io.yak.ops.flow.runtime.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.execution.TaskInfo;
import io.yak.ops.flow.runtime.source.coordinator.SourceCoordinator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SourceOperatorStreamTaskContextTest {

    @Test
    void shouldUseTaskInfoForReaderRegistrationAndSplitRequests() throws Exception {
        TestSource source = new TestSource();
        List<String> received = new CopyOnWriteArrayList<>();
        Configuration configuration = new Configuration();
        // 默认并行度与执行子任务的实际并行度不同，不应被当成 Reader parallelism。
        configuration.set(CoreOptions.DEFAULT_PARALLELISM, 8);
        TaskInfo info = new TaskInfo(JobID.generate(), 17, 1, 2, 0);

        try (SourceCoordinator<String, TestSplit, Integer> coordinator = new SourceCoordinator<>(source, 2)) {
            coordinator.start().get(5, TimeUnit.SECONDS);
            SourceOperatorStreamTask<String, TestSplit> task = new SourceOperatorStreamTask<>(
                    source, coordinator, new TaskEnvironment(info, configuration), received::add);

            task.start().get(5, TimeUnit.SECONDS);
            task.completionFuture().get(5, TimeUnit.SECONDS);

            assertEquals(info, task.taskInfo());
            assertEquals(List.of("split-1"), received);
            assertEquals(1, source.readerContext.getIndexOfSubtask());
            assertEquals(2, source.readerContext.currentParallelism());
            assertEquals(1, source.splitRequests.get());
            assertTrue(source.readerClosed.get());
        }
    }

    private record TestSplit(String splitId) implements SourceSplit {}

    private static final class TestSource implements Source<String, TestSplit, Integer> {

        private final AtomicInteger splitRequests = new AtomicInteger();
        private final AtomicBoolean readerClosed = new AtomicBoolean();
        private volatile SourceReaderContext readerContext;

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> createEnumerator(SplitEnumeratorContext<TestSplit> context) {
            return new TestEnumerator(context, splitRequests);
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> restoreEnumerator(
                SplitEnumeratorContext<TestSplit> context, Integer checkpointState) {
            throw new AssertionError("Fresh task must not restore an Enumerator");
        }

        @Override
        public SourceReader<String, TestSplit> createReader(SourceReaderContext context) {
            readerContext = context;
            return new TestReader(context, readerClosed);
        }

        @Override
        public SimpleVersionedSerializer<TestSplit> getSplitSerializer() {
            throw new AssertionError("Local event delivery must not require serialization");
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            throw new AssertionError("Local execution must not require checkpoint serialization");
        }
    }

    private static final class TestEnumerator implements SplitEnumerator<TestSplit, Integer> {

        private final SplitEnumeratorContext<TestSplit> context;
        private final AtomicInteger requests;

        private TestEnumerator(SplitEnumeratorContext<TestSplit> context, AtomicInteger requests) {
            this.context = context;
            this.requests = requests;
        }

        @Override
        public void start() {}

        @Override
        public void handleSplitRequest(int subtaskId) {
            requests.incrementAndGet();
            context.assignSplit(new TestSplit("split-" + subtaskId), subtaskId);
            context.signalNoMoreSplits(subtaskId);
        }

        @Override
        public void addReader(int subtaskId) {}

        @Override
        public void addSplitsBack(List<TestSplit> splits, int subtaskId) {
            throw new AssertionError("Unexpected split recovery");
        }

        @Override
        public Integer snapshotState(long checkpointId) {
            return 0;
        }

        @Override
        public void close() {}
    }

    private static final class TestReader implements SourceReader<String, TestSplit> {

        private final SourceReaderContext context;
        private final AtomicBoolean closed;
        private final List<TestSplit> pending = new ArrayList<>();
        private CompletableFuture<Void> available = new CompletableFuture<>();
        private boolean noMoreSplits;

        private TestReader(SourceReaderContext context, AtomicBoolean closed) {
            this.context = context;
            this.closed = closed;
        }

        @Override
        public void start() {
            context.sendSplitRequest();
        }

        @Override
        public InputStatus pollNext(ReaderOutput<String> output) throws Exception {
            if (!pending.isEmpty()) {
                output.collect(pending.removeFirst().splitId());
                return InputStatus.MORE_AVAILABLE;
            }
            if (noMoreSplits) {
                return InputStatus.END_OF_INPUT;
            }
            available = new CompletableFuture<>();
            return InputStatus.NOTHING_AVAILABLE;
        }

        @Override
        public CompletableFuture<Void> isAvailable() {
            return available;
        }

        @Override
        public void addSplits(List<TestSplit> splits) {
            pending.addAll(splits);
            available.complete(null);
        }

        @Override
        public void notifyNoMoreSplits() {
            noMoreSplits = true;
            available.complete(null);
        }

        @Override
        public List<TestSplit> snapshotState(long checkpointId) {
            return List.copyOf(pending);
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
