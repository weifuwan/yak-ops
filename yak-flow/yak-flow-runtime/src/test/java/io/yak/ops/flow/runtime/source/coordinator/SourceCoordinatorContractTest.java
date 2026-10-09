package io.yak.ops.flow.runtime.source.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.flow.runtime.support.TestSplitSerializers;
import io.yak.ops.flow.runtime.checkpoint.SourceCoordinatorCheckpoint;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.operators.coordination.OperatorCoordinatorContext;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;
import io.yak.ops.flow.runtime.source.event.AddSplitEvent;
import io.yak.ops.flow.runtime.source.event.NoMoreSplitsEvent;
import io.yak.ops.flow.runtime.source.event.RequestSplitEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SourceCoordinatorContractTest {

    @Test
    void shouldRejectMismatchedAndDuplicateReaderIdentity() throws Exception {
        JobID jobID = JobID.generate();
        assertThrows(IllegalArgumentException.class, () -> new OperatorCoordinatorContext(jobID, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new OperatorCoordinatorContext(jobID, 5, 0));
        assertThrows(NullPointerException.class, () -> new OperatorCoordinatorContext(null, 5, 2));

        OperatorCoordinatorContext context = new OperatorCoordinatorContext(jobID, 5, 2);
        RecordingSource source = new RecordingSource(false);
        RuntimeTaskInfo active = new RuntimeTaskInfo(jobID, 5, 1, 2, 0);

        try (SourceCoordinator<TestSplit, Integer> coordinator = new SourceCoordinator<>(source, context)) {
            assertEquals(context, coordinator.coordinatorContext());
            coordinator.start().get(5, TimeUnit.SECONDS);
            coordinator.registerReader(active, event -> CompletableFuture.completedFuture(null))
                    .get(5, TimeUnit.SECONDS);

            assertFailure(coordinator.registerReader(active, event -> CompletableFuture.completedFuture(null)));
            assertFailure(coordinator.registerReader(
                    new RuntimeTaskInfo(jobID, 5, 1, 2, 1), event -> CompletableFuture.completedFuture(null)));
            assertFailure(coordinator.registerReader(
                    new RuntimeTaskInfo(JobID.generate(), 5, 1, 2, 0), event -> CompletableFuture.completedFuture(null)));
            assertFailure(coordinator.registerReader(
                    new RuntimeTaskInfo(jobID, 6, 1, 2, 0), event -> CompletableFuture.completedFuture(null)));
            assertFailure(coordinator.registerReader(
                    new RuntimeTaskInfo(jobID, 5, 1, 3, 0), event -> CompletableFuture.completedFuture(null)));

            assertEquals(1, source.registrations.get());
        }
    }

    @Test
    void shouldRejectStaleAttemptAndMismatchedSplitEvent() throws Exception {
        JobID jobID = JobID.generate();
        RuntimeTaskInfo active = new RuntimeTaskInfo(jobID, 7, 1, 2, 2);
        RecordingSource source = new RecordingSource(false);

        try (SourceCoordinator<TestSplit, Integer> coordinator =
                new SourceCoordinator<>(source, new OperatorCoordinatorContext(jobID, 7, 2))) {
            coordinator.start().get(5, TimeUnit.SECONDS);
            coordinator.registerReader(active, event -> CompletableFuture.completedFuture(null))
                    .get(5, TimeUnit.SECONDS);

            assertFailure(coordinator.handleEventFromOperator(active, new RequestSplitEvent(1, 1)));
            assertFailure(coordinator.handleEventFromOperator(active, new RequestSplitEvent(0, 2)));
            RuntimeTaskInfo stale = new RuntimeTaskInfo(jobID, 7, 1, 2, 1);
            assertFailure(coordinator.handleEventFromOperator(stale, new RequestSplitEvent(1, 1)));
            assertFailure(coordinator.readerFailed(stale, new IllegalStateException("stale attempt")));
            assertFailure(coordinator.handleEventFromOperator(active, new NoMoreSplitsEvent()));
            assertFailure(coordinator.handleEventFromOperator(
                    new RuntimeTaskInfo(JobID.generate(), 7, 1, 2, 2), new RequestSplitEvent(1, 2)));

            coordinator.handleEventFromOperator(active, new RequestSplitEvent(1, 2))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(1, source.requests.get());
        }
    }

    @Test
    void shouldDeliverNoMoreSplitsOnlyAfterSplitEventAcknowledgment() throws Exception {
        JobID jobID = JobID.generate();
        RuntimeTaskInfo reader = new RuntimeTaskInfo(jobID, 9, 0, 1, 0);
        RecordingSource source = new RecordingSource(true);
        CompletableFuture<Void> splitAcknowledged = new CompletableFuture<>();
        CompletableFuture<Void> noMoreDispatched = new CompletableFuture<>();
        CompletableFuture<Void> noMoreAcknowledged = new CompletableFuture<>();
        List<OperatorEvent> delivered = new CopyOnWriteArrayList<>();

        try (SourceCoordinator<TestSplit, Integer> coordinator =
                new SourceCoordinator<>(source, new OperatorCoordinatorContext(jobID, 9, 1))) {
            coordinator.start().get(5, TimeUnit.SECONDS);
            coordinator.registerReader(reader, event -> {
                delivered.add(event);
                if (event instanceof AddSplitEvent<?>) {
                    return splitAcknowledged;
                }
                if (event instanceof NoMoreSplitsEvent) {
                    noMoreDispatched.complete(null);
                    return noMoreAcknowledged;
                }
                throw new AssertionError("Unexpected event: " + event);
            }).get(5, TimeUnit.SECONDS);

            coordinator.handleEventFromOperator(reader, new RequestSplitEvent(0, 0))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(1, delivered.size());
            assertInstanceOf(AddSplitEvent.class, delivered.getFirst());
            assertFailure(coordinator.snapshotCoordinator(7));

            splitAcknowledged.complete(null);
            noMoreDispatched.get(5, TimeUnit.SECONDS);
            assertEquals(2, delivered.size());
            assertInstanceOf(NoMoreSplitsEvent.class, delivered.get(1));
            assertFailure(coordinator.snapshotCoordinator(7));

            noMoreAcknowledged.complete(null);
            SourceCoordinatorCheckpoint<TestSplit, Integer> checkpoint = awaitCoordinatorSnapshot(coordinator, 7);
            assertEquals(7, checkpoint.checkpointId());
            assertEquals(List.of(new TestSplit("split-0")),
                    checkpoint.assignedSinceLastCompletedCheckpoint().get(0));
        }
    }

    @Test
    void shouldFailCoordinatorWhenSplitDeliveryFails() throws Exception {
        JobID jobID = JobID.generate();
        RuntimeTaskInfo reader = new RuntimeTaskInfo(jobID, 11, 0, 1, 0);
        RecordingSource source = new RecordingSource(true);
        CompletableFuture<Void> splitAck = new CompletableFuture<>();
        SourceCoordinator<TestSplit, Integer> coordinator =
                new SourceCoordinator<>(source, new OperatorCoordinatorContext(jobID, 11, 1));
        coordinator.start().get(5, TimeUnit.SECONDS);
        coordinator.registerReader(reader, event -> {
            if (event instanceof AddSplitEvent<?>) {
                return splitAck;
            }
            return CompletableFuture.completedFuture(null);
        }).get(5, TimeUnit.SECONDS);
        coordinator.handleEventFromOperator(reader, new RequestSplitEvent(0, 0)).get(5, TimeUnit.SECONDS);

        splitAck.completeExceptionally(new IllegalStateException("delivery failed"));
        ExecutionException thrown = assertThrows(
                ExecutionException.class, () -> coordinator.terminationFuture().get(5, TimeUnit.SECONDS));
        assertTrue(thrown.getCause().getMessage().contains("Split 交付失败"));
        assertTrue(source.closed.get());
    }

    @Test
    void shouldTreatReaderFailureAsWholeCoordinatorFailure() throws Exception {
        JobID jobID = JobID.generate();
        RuntimeTaskInfo reader = new RuntimeTaskInfo(jobID, 13, 0, 1, 1);
        RecordingSource source = new RecordingSource(false);
        SourceCoordinator<TestSplit, Integer> coordinator =
                new SourceCoordinator<>(source, new OperatorCoordinatorContext(jobID, 13, 1));

        coordinator.start().get(5, TimeUnit.SECONDS);
        coordinator.registerReader(reader, event -> CompletableFuture.completedFuture(null))
                .get(5, TimeUnit.SECONDS);
        coordinator.readerFailed(reader, new IllegalStateException("reader failure")).get(5, TimeUnit.SECONDS);

        ExecutionException failure = assertThrows(
                ExecutionException.class, () -> coordinator.terminationFuture().get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause().getMessage().contains("attempt=1"));
        assertTrue(source.closed.get());
    }

    private static void assertFailure(CompletableFuture<?> future) {
        assertThrows(CompletionException.class, future::join);
    }

    private static SourceCoordinatorCheckpoint<TestSplit, Integer> awaitCoordinatorSnapshot(
            SourceCoordinator<TestSplit, Integer> coordinator, long checkpointId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            try {
                return coordinator.snapshotCoordinator(checkpointId).join();
            } catch (CompletionException failure) {
                if (!(failure.getCause() instanceof IllegalStateException)
                        || !failure.getCause().getMessage().contains("尚未确认")
                        || System.nanoTime() >= deadline) {
                    throw failure;
                }
                Thread.sleep(5);
            }
        }
    }

    private record TestSplit(String splitId) implements SourceSplit {}

    private static final class RecordingSource implements Source<String, TestSplit, Integer> {

        private final boolean assignSplits;
        private final AtomicInteger registrations = new AtomicInteger();
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();

        private RecordingSource(boolean assignSplits) {
            this.assignSplits = assignSplits;
        }

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> createEnumerator(SplitEnumeratorContext<TestSplit> context) {
            return new SplitEnumerator<>() {
                @Override
                public void start() {}

                @Override
                public void handleSplitRequest(int subtaskId) {
                    requests.incrementAndGet();
                    if (assignSplits) {
                        context.assignSplit(new TestSplit("split-" + subtaskId), subtaskId);
                        context.signalNoMoreSplits(subtaskId);
                    }
                }

                @Override
                public void addReader(int subtaskId) {
                    registrations.incrementAndGet();
                }

                @Override
                public void addSplitsBack(List<TestSplit> splits, int subtaskId) {
                    throw new AssertionError("Not a per-reader recovery test");
                }

                @Override
                public Integer snapshotState(long checkpointId) {
                    return 1;
                }

                @Override
                public void close() {
                    closed.set(true);
                }
            };
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> restoreEnumerator(
                SplitEnumeratorContext<TestSplit> context, Integer checkpointState) {
            throw new AssertionError("Restore belongs to checkpoint acceptance");
        }

        @Override
        public SourceReader<String, TestSplit> createReader(SourceReaderContext context) {
            throw new AssertionError("Coordinator must never create Readers");
        }

        @Override
        public SimpleVersionedSerializer<TestSplit> getSplitSerializer() {
            return TestSplitSerializers.utf8(TestSplit::splitId, TestSplit::new);
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            throw new AssertionError("Local coordination does not serialize Checkpoint");
        }
    }
}
