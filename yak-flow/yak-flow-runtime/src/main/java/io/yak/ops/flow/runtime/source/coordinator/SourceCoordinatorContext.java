package io.yak.ops.flow.runtime.source.coordinator;

import io.yak.ops.core.api.connector.source.ReaderInfo;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.connector.source.SplitsAssignment;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.api.metrics.Gauge;
import io.yak.ops.core.api.metrics.groups.SplitEnumeratorMetricGroup;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.operators.coordination.OperatorCoordinatorContext;
import io.yak.ops.flow.runtime.operators.coordination.SubtaskGateway;
import io.yak.ops.flow.runtime.source.event.AddSplitEvent;
import io.yak.ops.flow.runtime.source.event.NoMoreSplitsEvent;
import io.yak.ops.flow.runtime.source.event.SourceEventWrapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
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
    private final ScheduledExecutorService discoveryExecutor;
    private final Supplier<Thread> coordinatorThread;
    private final Consumer<Throwable> onFailure;
    private final IntConsumer plannedSplits;
    private final SplitAssignmentTracker<SplitT> assignments = new SplitAssignmentTracker<>();
    private Gauge<Long> unassignedSplitsGauge;
    private final SplitEnumeratorMetricGroup metricGroup = new SplitEnumeratorMetricGroup() {
        @Override
        public <G extends Gauge<Long>> G setUnassignedSplitsGauge(G gauge) {
            assertCoordinatorThread();
            unassignedSplitsGauge = Objects.requireNonNull(gauge, "gauge");
            return gauge;
        }
    };
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
            ScheduledExecutorService discoveryExecutor,
            Supplier<Thread> coordinatorThread,
            Consumer<Throwable> onFailure,
            IntConsumer plannedSplits) {
        this.operatorContext = Objects.requireNonNull(operatorContext, "operatorContext must not be null");
        this.splitSerializer = Objects.requireNonNull(splitSerializer, "splitSerializer must not be null");
        this.coordinatorExecutor = Objects.requireNonNull(coordinatorExecutor);
        this.discoveryExecutor = Objects.requireNonNull(discoveryExecutor);
        this.coordinatorThread = Objects.requireNonNull(coordinatorThread);
        this.onFailure = Objects.requireNonNull(onFailure);
        this.plannedSplits = Objects.requireNonNull(plannedSplits);
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
    public void reportPlannedSplits(int count) {
        assertCoordinatorThread();
        plannedSplits.accept(count);
    }

    @Override
    public int currentParallelism() {
        return operatorContext.parallelism();
    }

    @Override
    public SplitEnumeratorMetricGroup metricGroup() {
        assertCoordinatorThread();
        return metricGroup;
    }

    @Override
    public Map<Integer, ReaderInfo> registeredReaders() {
        assertCoordinatorThread();
        Map<Integer, ReaderInfo> registered = new HashMap<>();
        readerIdentities.forEach((subtask, identity) -> registered.put(subtask, new ReaderInfo(subtask, null)));
        return Map.copyOf(registered);
    }

    @Override
    public Map<Integer, Map<Integer, ReaderInfo>> registeredReadersOfAttempts() {
        assertCoordinatorThread();
        Map<Integer, Map<Integer, ReaderInfo>> attempts = new HashMap<>();
        readerIdentities.forEach((subtask, task) ->
                attempts.put(subtask, Map.of(task.attemptNumber(), new ReaderInfo(subtask, null))));
        return Map.copyOf(attempts);
    }

    @Override
    public void assignSplits(SplitsAssignment<SplitT> newSplitAssignments) {
        assertCoordinatorThread();
        Map<Integer, List<SplitT>> requested =
                Objects.requireNonNull(newSplitAssignments, "newSplitAssignments").assignment();
        if (requested.isEmpty()) {
            return;
        }

        // Validate and serialize the entire batch before making any assignment visible.
        // A delivery failure afterwards fails the job and restores the last durable checkpoint.
        Set<String> splitIds = new LinkedHashSet<>();
        Set<String> outstandingIds = new HashSet<>(inFlight.keySet());
        assignments.outstandingAssignments().values().forEach(splits ->
                splits.forEach(split -> outstandingIds.add(split.splitId())));
        Map<Integer, AddSplitEvent<SplitT>> events = new HashMap<>();
        for (Map.Entry<Integer, List<SplitT>> entry : requested.entrySet()) {
            int reader = Objects.requireNonNull(entry.getKey(), "reader");
            if (!canAssignSplit(reader)) {
                throw new IllegalStateException("Cannot assign splits after NoMoreSplits: " + reader);
            }
            List<SplitT> splits = List.copyOf(Objects.requireNonNull(entry.getValue(), "splits"));
            if (splits.isEmpty()) {
                throw new IllegalArgumentException("Empty split assignment for reader " + reader);
            }
            for (SplitT split : splits) {
                String splitId = Objects.requireNonNull(split, "split").splitId();
                if (splitId == null || splitId.isBlank()
                        || !splitIds.add(splitId) || outstandingIds.contains(splitId)) {
                    throw new IllegalArgumentException("Duplicate or invalid split assignment: " + splitId);
                }
            }
            try {
                events.put(reader, new AddSplitEvent<>(splits, splitSerializer));
            } catch (java.io.IOException failure) {
                onFailure.accept(failure);
                throw new IllegalStateException("Split serialization failed", failure);
            }
        }

        for (Map.Entry<Integer, List<SplitT>> entry : requested.entrySet()) {
            int reader = entry.getKey();
            List<SplitT> splits = entry.getValue();
            List<String> ids = splits.stream().map(SourceSplit::splitId).toList();
            for (SplitT split : splits) {
                inFlight.put(split.splitId(), reader);
                assignments.recordAssignment(reader, split);
            }
            try {
                CompletionStage<Void> delivered = Objects.requireNonNull(
                        readers.get(reader).sendEvent(events.get(reader)), "SubtaskGateway returned null");
                delivered.whenComplete((unused, error) -> post(() -> {
                    if (error != null) {
                        onFailure.accept(new IllegalStateException("Split delivery failed: " + ids, error));
                    } else {
                        ids.forEach(inFlight::remove);
                        maybeSendNoMore(reader);
                    }
                }));
            } catch (Throwable failure) {
                onFailure.accept(failure);
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (failure instanceof Error serious) {
                    throw serious;
                }
                throw new IllegalStateException("Split delivery failed", failure);
            }
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
    public void sendEventToSourceReader(int subtaskId, int attemptNumber, SourceEvent event) {
        assertCoordinatorThread();
        RuntimeTaskInfo active = readerIdentities.get(subtaskId);
        if (active == null || active.attemptNumber() != attemptNumber) {
            throw new IllegalStateException("No registered reader attempt " + subtaskId + "/" + attemptNumber);
        }
        sendEventToSourceReader(subtaskId, event);
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
    public <T> void callAsync(
            Callable<T> action,
            BiConsumer<T, Throwable> handler,
            long initialDelayMillis,
            long periodMillis) {
        assertCoordinatorThread();
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(handler, "handler");
        if (initialDelayMillis < 0 || periodMillis <= 0) {
            throw new IllegalArgumentException("Invalid periodic discovery interval");
        }
        discoveryExecutor.scheduleWithFixedDelay(() -> {
            T value = null;
            Throwable error = null;
            try {
                value = action.call();
            } catch (Throwable failure) {
                error = failure;
            }
            T result = value;
            Throwable failure = error;
            post(() -> {
                Runnable callback = () -> handler.accept(result, failure);
                if (checkpointPaused) {
                    postponedDiscoveryCallbacks.add(callback);
                } else {
                    callback.run();
                }
            });
        }, initialDelayMillis, periodMillis, TimeUnit.MILLISECONDS);
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
