package io.yak.ops.flow.runtime.source.coordinator;

import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.operators.coordination.OperatorCoordinatorContext;
import io.yak.ops.flow.runtime.operators.coordination.SubtaskGateway;
import io.yak.ops.flow.runtime.source.event.AddSplitEvent;
import io.yak.ops.flow.runtime.source.event.NoMoreSplitsEvent;
import io.yak.ops.flow.runtime.source.event.SourceEventWrapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Split-coordination context confined to the SourceCoordinator event loop.
 *
 * <p>Manages reader registration, split delivery and asynchronous discovery.
 * State changes run on the coordinator thread; potentially blocking discovery
 * belongs to callAsync(). This context does not read data records.
 */
public final class SourceCoordinatorContext<SplitT extends SourceSplit> implements SplitEnumeratorContext<SplitT> {

    private final OperatorCoordinatorContext operatorContext;
    private final SimpleVersionedSerializer<SplitT> splitSerializer;
    private final ExecutorService coordinatorExecutor;
    private final ExecutorService discoveryExecutor;
    private final Supplier<Thread> coordinatorThread;
    private final Consumer<Throwable> onFailure;
    private final SplitAssignmentTracker<SplitT> assignments = new SplitAssignmentTracker<>();
    private final Map<Integer, SubtaskGateway> readers = new HashMap<>();
    private final Map<Integer, RuntimeTaskInfo> readerIdentities = new HashMap<>();
    private final Map<String, Integer> inFlight = new HashMap<>();
    private final Set<CompletionStage<Void>> pendingSourceEvents = new HashSet<>();
    private final Set<Integer> noMoreRequested = new HashSet<>();
    private final Set<Integer> noMoreDispatched = new HashSet<>();
    private final Set<Integer> pendingNoMore = new HashSet<>();
    private final List<Runnable> postponedDiscoveryCallbacks = new ArrayList<>();
    private boolean checkpointPaused;
    private volatile boolean closed;

    SourceCoordinatorContext(
            OperatorCoordinatorContext operatorContext,
            SimpleVersionedSerializer<SplitT> splitSerializer,
            ExecutorService coordinatorExecutor,
            ExecutorService discoveryExecutor,
            Supplier<Thread> coordinatorThread,
            Consumer<Throwable> onFailure) {
        this.operatorContext = Objects.requireNonNull(operatorContext, "operatorContext must not be null");
        this.splitSerializer = Objects.requireNonNull(splitSerializer, "splitSerializer must not be null");
        this.coordinatorExecutor = Objects.requireNonNull(coordinatorExecutor);
        this.discoveryExecutor = Objects.requireNonNull(discoveryExecutor);
        this.coordinatorThread = Objects.requireNonNull(coordinatorThread);
        this.onFailure = Objects.requireNonNull(onFailure);
    }

    void registerReader(RuntimeTaskInfo taskInfo, SubtaskGateway gateway) {
        assertCoordinatorThread();
        if (checkpointPaused) {
            throw new IllegalStateException("Cannot register readers during checkpoint alignment");
        }
        operatorContext.validateTask(taskInfo);
        int subtaskId = taskInfo.subtaskIndex();
        checkSubtask(subtaskId);
        Objects.requireNonNull(gateway, "gateway must not be null");
        if (readers.putIfAbsent(subtaskId, gateway) != null) {
            throw new IllegalArgumentException(
                    "Reader is already registered; replacing an individual reader attempt is unsupported: "
                            + subtaskId);
        }
        readerIdentities.put(subtaskId, taskInfo);
    }

    void checkRegistered(RuntimeTaskInfo taskInfo) {
        assertCoordinatorThread();
        operatorContext.validateTask(taskInfo);
        RuntimeTaskInfo active = readerIdentities.get(taskInfo.subtaskIndex());
        if (!taskInfo.equals(active)) {
            throw new IllegalStateException("Reader is unregistered or stale, subtask=" + taskInfo.subtaskIndex()
                    + ", attempt=" + taskInfo.attemptNumber());
        }
    }

    boolean canRequestSplit(RuntimeTaskInfo taskInfo) {
        checkRegistered(taskInfo);
        return !noMoreRequested.contains(taskInfo.subtaskIndex());
    }

    private boolean canAssignSplit(int subtaskId) {
        checkSubtask(subtaskId);
        if (!readerIdentities.containsKey(subtaskId)) {
            throw new IllegalStateException("Reader is not registered: " + subtaskId);
        }
        return !noMoreRequested.contains(subtaskId);
    }

    void pauseForCheckpoint() {
        assertCoordinatorThread();
        if (checkpointPaused) {
            throw new IllegalStateException("Coordinator context is paused");
        }
        checkpointPaused = true;
    }

    void resumeAfterCheckpoint() {
        assertCoordinatorThread();
        checkpointPaused = false;
        List<Runnable> deferred = List.copyOf(postponedDiscoveryCallbacks);
        postponedDiscoveryCallbacks.clear();
        for (Runnable callback : deferred) {
            callback.run();
        }
    }

    void ensureDeliveriesCompleted() {
        assertCoordinatorThread();
        if (!inFlight.isEmpty() || !pendingNoMore.isEmpty()) {
            throw new PendingCheckpointDeliveriesException();
        }
    }

    /** Waits for SourceEvents emitted by a completed checkpoint callback to reach the Reader. */
    CompletableFuture<Void> awaitSourceEventDeliveries() {
        assertCoordinatorThread();
        return CompletableFuture.allOf(pendingSourceEvents.stream()
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture<?>[]::new));
    }

    SplitAssignmentTracker<SplitT> assignmentTracker() {
        assertCoordinatorThread();
        return assignments;
    }

    void dispose() {
        assertCoordinatorThread();
        closed = true;
        readers.clear();
        readerIdentities.clear();
        postponedDiscoveryCallbacks.clear();
        inFlight.clear();
        pendingSourceEvents.clear();
        pendingNoMore.clear();
        noMoreRequested.clear();
        noMoreDispatched.clear();
    }

    @Override
    public int currentParallelism() {
        return operatorContext.parallelism();
    }

    @Override
    public Set<Integer> registeredReaders() {
        assertCoordinatorThread();
        return Set.copyOf(readers.keySet());
    }

    @Override
    public void assignSplit(SplitT split, int subtaskId) {
        assertCoordinatorThread();
        if (!canAssignSplit(subtaskId)) {
            throw new IllegalStateException("Cannot assign splits after NoMoreSplits: " + subtaskId);
        }
        String splitId = Objects.requireNonNull(split, "split").splitId();
        if (inFlight.putIfAbsent(splitId, subtaskId) != null) {
            throw new IllegalArgumentException("Duplicate in-flight split delivery: " + splitId);
        }
        try {
            AddSplitEvent<SplitT> event = new AddSplitEvent<>(List.of(split), splitSerializer);
            assignments.recordAssignment(subtaskId, split);
            CompletionStage<Void> delivered =
                    Objects.requireNonNull(readers.get(subtaskId).sendEvent(event), "SubtaskGateway returned null");
            delivered.whenComplete((unused, error) -> post(() -> {
                if (error != null) {
                    onFailure.accept(new IllegalStateException("Split delivery failed: " + splitId, error));
                } else {
                    inFlight.remove(splitId);
                    maybeSendNoMore(subtaskId);
                }
            }));
        } catch (Throwable error) {
            onFailure.accept(error);
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (error instanceof Error serious) {
                throw serious;
            }
            throw new IllegalStateException("Split serialization or delivery failed", error);
        }
    }

    @Override
    public void signalNoMoreSplits(int subtaskId) {
        assertCoordinatorThread();
        checkSubtask(subtaskId);
        if (!readers.containsKey(subtaskId)) {
            throw new IllegalArgumentException("Reader is not registered: " + subtaskId);
        }
        noMoreRequested.add(subtaskId);
        maybeSendNoMore(subtaskId);
    }

    private void maybeSendNoMore(int subtaskId) {
        if (!noMoreRequested.contains(subtaskId)
                || noMoreDispatched.contains(subtaskId)
                || inFlight.containsValue(subtaskId)) {
            return;
        }
        noMoreDispatched.add(subtaskId);
        pendingNoMore.add(subtaskId);
        try {
            CompletionStage<Void> delivered = Objects.requireNonNull(
                    readers.get(subtaskId).sendEvent(new NoMoreSplitsEvent()), "SubtaskGateway returned null");
            delivered.whenComplete((unused, error) -> post(() -> {
                if (error != null) {
                    onFailure.accept(new IllegalStateException("NoMoreSplits delivery failed: " + subtaskId, error));
                } else {
                    pendingNoMore.remove(subtaskId);
                }
            }));
        } catch (Throwable error) {
            onFailure.accept(error);
            throw error;
        }
    }

    @Override
    public void sendEventToSourceReader(int subtaskId, SourceEvent event) {
        assertCoordinatorThread();
        checkSubtask(subtaskId);
        Objects.requireNonNull(event, "event must not be null");
        SubtaskGateway gateway = readers.get(subtaskId);
        if (gateway == null) {
            throw new IllegalStateException("Reader is not registered: " + subtaskId);
        }
        try {
            CompletionStage<Void> delivered = Objects.requireNonNull(
                    gateway.sendEvent(new SourceEventWrapper(event)), "SubtaskGateway returned null");
            pendingSourceEvents.add(delivered);
            delivered.whenComplete((unused, failure) -> post(() -> {
                pendingSourceEvents.remove(delivered);
                if (failure != null) {
                    onFailure.accept(
                            new IllegalStateException("SourceEvent delivery failed: subtask=" + subtaskId, failure));
                }
            }));
        } catch (Throwable failure) {
            onFailure.accept(failure);
            throw failure;
        }
    }

    @Override
    public <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler) {
        assertCoordinatorThread();
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        discoveryExecutor.execute(() -> {
            T value = null;
            Throwable error = null;
            try {
                value = action.call();
            } catch (Throwable failure) {
                error = failure;
            }
            T finishedValue = value;
            Throwable finishedError = error;
            post(() -> {
                Runnable callback = () -> handler.accept(finishedValue, finishedError);
                if (checkpointPaused) {
                    postponedDiscoveryCallbacks.add(callback);
                } else {
                    callback.run();
                }
            });
        });
    }

    @Override
    public void runInCoordinatorThread(Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        Runnable guarded = () -> {
            if (checkpointPaused) {
                postponedDiscoveryCallbacks.add(action);
            } else {
                action.run();
            }
        };
        if (Thread.currentThread() == coordinatorThread.get()) {
            guarded.run();
        } else {
            post(guarded);
        }
    }

    private void post(Runnable action) {
        if (closed) {
            return;
        }
        try {
            coordinatorExecutor.execute(() -> {
                if (closed) {
                    return;
                }
                try {
                    action.run();
                } catch (Throwable failure) {
                    onFailure.accept(failure);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Late asynchronous callbacks must not mutate state after coordinator shutdown.
        }
    }

    private void checkSubtask(int subtaskId) {
        if (subtaskId < 0 || subtaskId >= currentParallelism()) {
            throw new IllegalArgumentException("Reader subtask index is out of range: " + subtaskId);
        }
    }

    private void assertCoordinatorThread() {
        if (Thread.currentThread() != coordinatorThread.get()) {
            throw new IllegalStateException("SourceCoordinatorContext must be accessed on the coordinator thread");
        }
    }
}
