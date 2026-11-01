package io.yak.ops.flow.runtime.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.flow.runtime.checkpoint.CheckpointDeclinedException;
import io.yak.ops.flow.runtime.support.TestSplitSerializers;
import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.graph.StreamEdge;
import io.yak.ops.flow.runtime.io.RecordWriterOutput;
import io.yak.ops.flow.runtime.io.partition.InputGate;
import io.yak.ops.flow.runtime.io.partition.ResultPartition;
import io.yak.ops.flow.runtime.operators.coordination.OperatorCoordinatorContext;
import io.yak.ops.flow.runtime.source.coordinator.SourceCoordinator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SourceOperatorStreamTaskContextTest {

    @Test
    void shouldUseTaskInfoForReaderRegistrationAndSplitRequests() throws Exception {
        TestSource source = new TestSource();
        List<String> received = new CopyOnWriteArrayList<>();
        Configuration configuration = new Configuration();
        // The configured default differs from actual task parallelism and must not override reader identity.
        configuration.set(CoreOptions.DEFAULT_PARALLELISM, 8);
        RuntimeTaskInfo info = new RuntimeTaskInfo(JobID.generate(), 17, 1, 2, 0, 128);

        OperatorCoordinatorContext coordinatorContext =
                new OperatorCoordinatorContext(info.jobID(), info.operatorId(), info.parallelism());
        try (SourceCoordinator<TestSplit, Integer> coordinator = new SourceCoordinator<>(source, coordinatorContext)) {
            coordinator.start().get(5, TimeUnit.SECONDS);
            SourceOperatorStreamTask<String, TestSplit> task = new SourceOperatorStreamTask<>(
                    source, coordinator, new TaskEnvironment(info, configuration), received::add);

            task.start().get(5, TimeUnit.SECONDS);
            task.completionFuture().get(5, TimeUnit.SECONDS);

            assertEquals(info, task.taskInfo());
            assertEquals(List.of("split-1"), received);
            assertEquals(1, source.readerContext.getIndexOfSubtask());
            assertEquals(2, source.readerContext.currentParallelism());
            assertEquals(8, (int) source.readerContext.getConfiguration().get(CoreOptions.DEFAULT_PARALLELISM));
            Configuration exposed = source.readerContext.getConfiguration();
            exposed.set(CoreOptions.DEFAULT_PARALLELISM, 99);
            assertEquals(8, (int) source.readerContext.getConfiguration().get(CoreOptions.DEFAULT_PARALLELISM));
            assertEquals(1, source.splitRequests.get());
            assertEquals(new ProbeSourceEvent("hello"), source.readerSourceEvent.get());
            assertTrue(source.readerClosed.get());
        }
    }

    @Test
    void restoredReaderCanReportMetadataAfterRegistrationAndBeforeStart() throws Exception {
        for (int attempt : List.of(0, 1)) {
            TestSource source = new TestSource(true);
            List<String> received = new CopyOnWriteArrayList<>();
            RuntimeTaskInfo info = new RuntimeTaskInfo(JobID.generate(), 17, 0, 1, attempt, 128);
            OperatorCoordinatorContext coordinatorContext =
                    new OperatorCoordinatorContext(info.jobID(), info.operatorId(), info.parallelism());
            try (SourceCoordinator<TestSplit, Integer> coordinator = new SourceCoordinator<>(source, coordinatorContext)) {
                coordinator.start().get(5, TimeUnit.SECONDS);
                SourceOperatorStreamTask<String, TestSplit> task = new SourceOperatorStreamTask<>(
                        source, coordinator, new TaskEnvironment(info, new Configuration()), received::add,
                        null, List.of(new TestSplit("checkpointed")));
                task.start().get(5, TimeUnit.SECONDS);
                task.completionFuture().get(5, TimeUnit.SECONDS);
                assertEquals(1, source.restoredEvents.get());
                assertEquals(1, source.registrationEvents.get());
                assertTrue(source.startedWithRestoredSplits.get());
                assertEquals(List.of("checkpointed", "split-0"), received);
                assertTrue(source.readerClosed.get());
            }
        }
    }

    @Test
    void finishedPhysicalSourceOutputDeclinesCheckpointWhileReaderIsClosing() throws Exception {
        TestSource source = new TestSource();
        source.holdReaderClose();
        Configuration configuration = new Configuration();
        RuntimeTaskInfo info = new RuntimeTaskInfo(JobID.generate(), 17, 0, 1, 0, 128);
        InputGate<String> gate = new InputGate<>(4, 1);
        RecordWriterOutput<String> writer = new RecordWriterOutput<>(
                new StreamEdge(17, 18), 0, 1, new ResultPartition<>(0, List.of(gate)));
        OperatorCoordinatorContext context = new OperatorCoordinatorContext(info.jobID(), 17, 1);

        try (SourceCoordinator<TestSplit, Integer> coordinator = new SourceCoordinator<>(source, context)) {
            coordinator.start().get(5, TimeUnit.SECONDS);
            SourceOperatorStreamTask<String, TestSplit> task = new SourceOperatorStreamTask<>(
                    source, coordinator, new TaskEnvironment(info, configuration), writer);
            task.start().get(5, TimeUnit.SECONDS);
            List<String> rows = new ArrayList<>();
            try {
                while (true) {
                    InputStatus status = gate.emitNext(rows::add);
                    if (status == InputStatus.END_OF_INPUT) {
                        break;
                    }
                    if (status == InputStatus.NOTHING_AVAILABLE) {
                        gate.getAvailableFuture().get(5, TimeUnit.SECONDS);
                    }
                }
                assertTrue(source.readerCloseEntered.await(5, TimeUnit.SECONDS));
                assertTrue(task.hasFinishedOutput());
                assertFalse(task.completionFuture().isDone());
                ExecutionException declined = assertThrows(ExecutionException.class,
                        () -> task.pauseAndEmitBarrier(17).get(5, TimeUnit.SECONDS));
                assertTrue(declined.getCause() instanceof CheckpointDeclinedException);
                assertEquals(List.of("split-0"), rows);
            } finally {
                source.releaseReaderClose.countDown();
            }
            task.completionFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldRejectMismatchedTaskBeforeOpeningReader() throws Exception {
        TestSource source = new TestSource();
        JobID jobID = JobID.generate();
        RuntimeTaskInfo wrong = new RuntimeTaskInfo(JobID.generate(), 17, 1, 2, 0, 128);
        try (SourceCoordinator<TestSplit, Integer> coordinator =
                new SourceCoordinator<>(source, new OperatorCoordinatorContext(jobID, 17, 2))) {
            assertThrows(IllegalArgumentException.class, () -> new SourceOperatorStreamTask<>(
                    source, coordinator, new TaskEnvironment(wrong, new Configuration()), ignored -> {}));
        }
    }

    private record TestSplit(String splitId) implements SourceSplit {}

    private record ProbeSourceEvent(String message) implements SourceEvent {}

    private static final class TestSource implements Source<String, TestSplit, Integer> {

        private final AtomicInteger splitRequests = new AtomicInteger();
        private final AtomicInteger restoredEvents = new AtomicInteger();
        private final AtomicInteger registrationEvents = new AtomicInteger();
        private final AtomicBoolean startedWithRestoredSplits = new AtomicBoolean();
        private final AtomicBoolean readerClosed = new AtomicBoolean();
        private final boolean reportRestored;
        private CountDownLatch readerCloseEntered = new CountDownLatch(0);
        private CountDownLatch releaseReaderClose = new CountDownLatch(0);
        private volatile SourceReaderContext readerContext;

        private TestSource() {
            this(false);
        }

        private TestSource(boolean reportRestored) {
            this.reportRestored = reportRestored;
        }

        private void holdReaderClose() {
            readerCloseEntered = new CountDownLatch(1);
            releaseReaderClose = new CountDownLatch(1);
        }
        private final AtomicReference<SourceEvent> readerSourceEvent = new AtomicReference<>();

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> createEnumerator(SplitEnumeratorContext<TestSplit> context) {
            return new TestEnumerator(context, splitRequests, restoredEvents, reportRestored);
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> restoreEnumerator(
                SplitEnumeratorContext<TestSplit> context, Integer checkpointState) {
            throw new AssertionError("Fresh task must not restore an Enumerator");
        }

        @Override
        public SourceReader<String, TestSplit> createReader(SourceReaderContext context) {
            readerContext = context;
            return new TestReader(context, readerClosed, readerSourceEvent,
                    readerCloseEntered, releaseReaderClose, reportRestored, startedWithRestoredSplits, registrationEvents);
        }

        @Override
        public SimpleVersionedSerializer<TestSplit> getSplitSerializer() {
            return TestSplitSerializers.utf8(TestSplit::splitId, TestSplit::new);
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            throw new AssertionError("Local execution must not require checkpoint serialization");
        }
    }

    private static final class TestEnumerator implements SplitEnumerator<TestSplit, Integer> {

        private final SplitEnumeratorContext<TestSplit> context;
        private final AtomicInteger requests;
        private final AtomicInteger restoredEvents;
        private final boolean reportRestored;

        private TestEnumerator(SplitEnumeratorContext<TestSplit> context, AtomicInteger requests, AtomicInteger restoredEvents,
                boolean reportRestored) {
            this.context = context;
            this.requests = requests;
            this.restoredEvents = restoredEvents;
            this.reportRestored = reportRestored;
        }

        @Override
        public void start() {}

        @Override
        public void handleSplitRequest(int subtaskId, String requesterHostname) {
            requests.incrementAndGet();
            context.assignSplit(new TestSplit("split-" + subtaskId), subtaskId);
            context.sendEventToSourceReader(subtaskId, new ProbeSourceEvent("hello"));
            context.signalNoMoreSplits(subtaskId);
        }

        @Override
        public void addReader(int subtaskId) {
            if (reportRestored) context.sendEventToSourceReader(subtaskId, new ProbeSourceEvent("registered"));
        }

        @Override
        public void handleSourceEvent(int subtaskId, SourceEvent event) {
            assertEquals(new ProbeSourceEvent("restored"), event);
            assertTrue(context.registeredReaders().containsKey(subtaskId));
            restoredEvents.incrementAndGet();
        }

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
        private final AtomicReference<SourceEvent> receivedSourceEvent;
        private final CountDownLatch readerCloseEntered;
        private final CountDownLatch releaseReaderClose;
        private final boolean reportRestored;
        private final AtomicBoolean startedWithRestoredSplits;
        private final AtomicInteger registrationEvents;
        private final List<TestSplit> pending = new ArrayList<>();
        private CompletableFuture<Void> available = new CompletableFuture<>();
        private boolean noMoreSplits;
        private boolean started;

        private TestReader(
                SourceReaderContext context,
                AtomicBoolean closed,
                AtomicReference<SourceEvent> receivedSourceEvent,
                CountDownLatch readerCloseEntered,
                CountDownLatch releaseReaderClose,
                boolean reportRestored,
                AtomicBoolean startedWithRestoredSplits,
                AtomicInteger registrationEvents) {
            this.context = context;
            this.closed = closed;
            this.receivedSourceEvent = receivedSourceEvent;
            this.readerCloseEntered = readerCloseEntered;
            this.releaseReaderClose = releaseReaderClose;
            this.reportRestored = reportRestored;
            this.startedWithRestoredSplits = startedWithRestoredSplits;
            this.registrationEvents = registrationEvents;
        }

        @Override
        public void start() {
            startedWithRestoredSplits.set(!pending.isEmpty());
            started = true;
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
            if (reportRestored && !started) {
                context.sendSourceEventToCoordinator(new ProbeSourceEvent("restored"));
            }
            available.complete(null);
        }

        @Override
        public void handleSourceEvents(SourceEvent event) {
            if (event.equals(new ProbeSourceEvent("registered"))) {
                assertTrue(started, "Registration callback must wait for the reader mailbox to start");
                assertTrue(startedWithRestoredSplits.get(), "Restored offsets must precede registration callback consumption");
                registrationEvents.incrementAndGet();
            }
            receivedSourceEvent.set(event);
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
        public void close() throws Exception {
            readerCloseEntered.countDown();
            releaseReaderClose.await();
            closed.set(true);
        }
    }
}
