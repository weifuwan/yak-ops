package io.yak.ops.flow.runtime.source.coordinator;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.flow.runtime.checkpoint.SourceCoordinatorCheckpoint;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.operators.coordination.OperatorCoordinatorContext;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;
import io.yak.ops.flow.runtime.operators.coordination.SubtaskGateway;
import io.yak.ops.flow.runtime.source.event.ReaderRegistrationEvent;
import io.yak.ops.flow.runtime.source.event.RequestSplitEvent;
import io.yak.ops.flow.runtime.source.event.SourceEventWrapper;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Event-loop-owned coordinator for a Source's enumerator and reader registrations.
 *
 * <p>SourceCoordinatorContext owns split delivery acknowledgments, outstanding assignments
 * and asynchronous discovery. This class does not read records or implement split algorithms.
 * Recovery restarts the whole job, not a single Reader attempt. Coordinator state alone
 * is not a completed job checkpoint.
 */
public final class SourceCoordinator<SplitT extends SourceSplit, EnumStateT> implements AutoCloseable {

    private final Source<?, SplitT, EnumStateT> source;
    private final OperatorCoordinatorContext coordinatorContext;
    private final EnumStateT restoredEnumeratorState;
    private final boolean restoring;
    private final ExecutorService eventLoop = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("yak-source-coordinator-", 0).factory());
    private final ExecutorService discovery = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("yak-source-discovery-", 0).factory());
    private final SourceCoordinatorContext<SplitT> context;
    private final AtomicBoolean startRequested = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private volatile Thread eventThread;
    private SplitEnumerator<SplitT, EnumStateT> enumerator;
    private boolean started;
    private boolean checkpointPaused;
    private final Set<Integer> deferredSplitRequests = new LinkedHashSet<>();

    public SourceCoordinator(Source<?, SplitT, EnumStateT> source, OperatorCoordinatorContext coordinatorContext) {
        this(source, coordinatorContext, null, false);
    }

    /** Restores enumerator state from a completed checkpoint; reader splits are restored separately. */
    public static <SplitT extends SourceSplit, EnumStateT> SourceCoordinator<SplitT, EnumStateT> restore(
            Source<?, SplitT, EnumStateT> source, OperatorCoordinatorContext coordinatorContext, EnumStateT state) {
        return new SourceCoordinator<>(source, coordinatorContext, Objects.requireNonNull(state, "恢复状态不能为空"), true);
    }

    private SourceCoordinator(
            Source<?, SplitT, EnumStateT> source,
            OperatorCoordinatorContext coordinatorContext,
            EnumStateT restoredEnumeratorState,
            boolean restoring) {
        this.source = Objects.requireNonNull(source, "source 不能为空");
        this.coordinatorContext = Objects.requireNonNull(coordinatorContext, "coordinatorContext 不能为空");
        this.restoredEnumeratorState = restoredEnumeratorState;
        this.restoring = restoring;
        this.context = new SourceCoordinatorContext<>(
                coordinatorContext, source.getSplitSerializer(), eventLoop, discovery, () -> eventThread, this::fail);
    }

    /** Returns the identity used to validate the owning job, operator and parallelism. */
    public OperatorCoordinatorContext coordinatorContext() {
        return coordinatorContext;
    }

    /**
     * Creates and starts the enumerator once on the coordinator event loop.
     *
     * @return a future completed after initialization or exceptionally on failure
     */
    public CompletableFuture<Void> start() {
        if (!startRequested.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("SourceCoordinator 已启动"));
        }
        return submit(() -> {
            try {
                enumerator = Objects.requireNonNull(
                        restoring
                                ? source.restoreEnumerator(context, restoredEnumeratorState)
                                : source.createEnumerator(context),
                        "Source 返回了空 Enumerator");
                enumerator.start();
                started = true;
                return null;
            } catch (Throwable failure) {
                fail(failure);
                throw failure;
            }
        });
    }

    /** Registers an active Reader gateway without waiting for split consumption. */
    public CompletableFuture<Void> registerReader(RuntimeTaskInfo taskInfo, SubtaskGateway gateway) {
        Objects.requireNonNull(gateway, "gateway 不能为空");
        try {
            coordinatorContext.validateTask(taskInfo);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        ReaderRegistrationEvent event = new ReaderRegistrationEvent(taskInfo.subtaskIndex(), taskInfo.attemptNumber());
        return submit(() -> {
            ensureStarted();
            context.registerReader(taskInfo, gateway);
            try {
                enumerator.addReader(event.subtaskId());
                return null;
            } catch (Throwable failure) {
                fail(failure);
                throw failure;
            }
        });
    }

    /**
     * Processes a control event from an active SourceOperator attempt.
     *
     * <p>Split requests must match the registered subtask and attempt. Connector events
     * are also validated before delivery to the enumerator; stale attempts are rejected.
     */
    public CompletableFuture<Void> handleEventFromOperator(RuntimeTaskInfo taskInfo, OperatorEvent event) {
        Objects.requireNonNull(event, "event 不能为空");
        try {
            coordinatorContext.validateTask(taskInfo);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        if (event instanceof RequestSplitEvent request) {
            if (taskInfo.subtaskIndex() != request.subtaskId() || taskInfo.attemptNumber() != request.attemptNumber()) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("OperatorEvent 的 Subtask / Attempt 与发送者身份不匹配"));
            }
        } else if (!(event instanceof SourceEventWrapper)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Coordinator 不支持的 OperatorEvent：" + event.getClass().getName()));
        }
        return submit(() -> {
            ensureStarted();
            // Check the registered attempt before handling either control or connector event.
            context.checkRegistered(taskInfo);
            if (event instanceof SourceEventWrapper wrapper) {
                try {
                    enumerator.handleSourceEvent(taskInfo.subtaskIndex(), wrapper.sourceEvent());
                } catch (Throwable failure) {
                    fail(failure);
                    throw failure;
                }
                return null;
            }
            if (!context.canRequestSplit(taskInfo)) {
                return null;
            }
            if (checkpointPaused) {
                deferredSplitRequests.add(taskInfo.subtaskIndex());
                return null;
            }
            try {
                enumerator.handleSplitRequest(taskInfo.subtaskIndex());
                return null;
            } catch (Throwable failure) {
                fail(failure);
                throw failure;
            }
        });
    }

    /** Pauses new split requests without discarding outstanding delivery acknowledgments. */
    public CompletableFuture<Void> pauseForCheckpoint() {
        return submit(() -> {
            ensureStarted();
            if (checkpointPaused) {
                throw new IllegalStateException("SourceCoordinator 已暂停");
            }
            checkpointPaused = true;
            context.pauseForCheckpoint();
            return null;
        });
    }

    /** Waits until split and no-more-splits events are processed by reader mailboxes. */
    public CompletableFuture<Void> awaitCheckpointDeliveries() {
        return submit(() -> {
            ensureStarted();
            context.ensureDeliveriesCompleted();
            return null;
        });
    }

    /** Resumes deferred split requests and discovery callbacks after a checkpoint attempt. */
    public CompletableFuture<Void> resumeAfterCheckpoint() {
        return submit(() -> {
            if (checkpointPaused) {
                checkpointPaused = false;
                context.resumeAfterCheckpoint();
                for (Integer subtask : deferredSplitRequests) {
                    if (context.registeredReaders().contains(subtask)) {
                        enumerator.handleSplitRequest(subtask);
                    }
                }
                deferredSplitRequests.clear();
            }
            return null;
        });
    }

    /** Captures enumerator-local state, not a complete job checkpoint. */
    public CompletableFuture<EnumStateT> snapshotEnumerator(long checkpointId) {
        validateCheckpointId(checkpointId);
        return submit(() -> {
            ensureStarted();
            context.ensureDeliveriesCompleted();
            return Objects.requireNonNull(enumerator.snapshotState(checkpointId), "Enumerator 快照不能为空");
        });
    }

    /**
     * Captures coordinator state and outstanding split assignments.
     *
     * <p>Readers, channels and Sinks must be aligned and the entire state persisted before
     * a checkpoint can be announced as successfully completed.
     */
    public CompletableFuture<SourceCoordinatorCheckpoint<SplitT, EnumStateT>> snapshotCoordinator(long checkpointId) {
        validateCheckpointId(checkpointId);
        return submit(() -> {
            ensureStarted();
            context.ensureDeliveriesCompleted();
            EnumStateT state = Objects.requireNonNull(enumerator.snapshotState(checkpointId), "Enumerator 快照不能为空");
            Map<Integer, java.util.List<SplitT>> assignments =
                    context.assignmentTracker().snapshot(checkpointId);
            return new SourceCoordinatorCheckpoint<>(checkpointId, state, assignments);
        });
    }

    /** Commits assignment history only after the complete job checkpoint is durable. */
    public CompletableFuture<Void> notifyCheckpointComplete(long checkpointId) {
        validateCheckpointId(checkpointId);
        return submit(() -> {
            ensureStarted();
            enumerator.notifyCheckpointComplete(checkpointId);
            context.assignmentTracker().notifyCheckpointComplete(checkpointId);
            return null;
        });
    }

    /** Aborts an unsuccessful checkpoint without discarding outstanding assignments. */
    public CompletableFuture<Void> notifyCheckpointAborted(long checkpointId) {
        validateCheckpointId(checkpointId);
        return submit(() -> {
            context.assignmentTracker().notifyCheckpointAborted(checkpointId);
            return null;
        });
    }

    /** Fails the coordinator so the runtime can recover the whole job, not an individual Reader. */
    public CompletableFuture<Void> readerFailed(RuntimeTaskInfo taskInfo, Throwable failure) {
        Objects.requireNonNull(failure, "failure 不能为空");
        return submit(() -> {
            context.checkRegistered(taskInfo);
            fail(new IllegalStateException(
                    "Source Reader 故障，作业需要整体恢复：" + taskInfo.subtaskIndex() + "，attempt=" + taskInfo.attemptNumber(),
                    failure));
            return null;
        });
    }

    public CompletableFuture<Void> terminationFuture() {
        return termination.copy();
    }

    public CompletableFuture<Void> closeAsync() {
        if (closing.compareAndSet(false, true)) {
            try {
                eventLoop.execute(() -> {
                    eventThread = Thread.currentThread();
                    cleanup(null);
                });
            } catch (RejectedExecutionException error) {
                cleanup(error);
            }
        }
        return termination.copy();
    }

    @Override
    public void close() throws Exception {
        if (Thread.currentThread() == eventThread) {
            closeAsync();
            return;
        }
        try {
            closeAsync().get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw failure;
        } catch (ExecutionException failure) {
            throw new Exception("SourceCoordinator 关闭失败", failure.getCause());
        }
    }

    private void ensureStarted() {
        if (!started || enumerator == null) {
            throw new IllegalStateException("Enumerator 尚未启动");
        }
    }

    private static void validateCheckpointId(long checkpointId) {
        if (checkpointId < 0) {
            throw new IllegalArgumentException("checkpointId 不能为负数");
        }
    }

    private <V> CompletableFuture<V> submit(Callable<V> action) {
        if (closing.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("SourceCoordinator 已关闭"));
        }
        CompletableFuture<V> reply = new CompletableFuture<>();
        try {
            eventLoop.execute(() -> {
                eventThread = Thread.currentThread();
                if (closing.get()) {
                    reply.completeExceptionally(new IllegalStateException("SourceCoordinator 已关闭"));
                    return;
                }
                try {
                    reply.complete(action.call());
                } catch (Throwable failure) {
                    reply.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            reply.completeExceptionally(failure);
        }
        return reply;
    }

    private void fail(Throwable failure) {
        if (closing.compareAndSet(false, true)) {
            if (Thread.currentThread() == eventThread) {
                cleanup(failure);
            } else {
                try {
                    eventLoop.execute(() -> {
                        eventThread = Thread.currentThread();
                        cleanup(failure);
                    });
                } catch (RejectedExecutionException error) {
                    failure.addSuppressed(error);
                    cleanup(failure);
                }
            }
        }
    }

    private void cleanup(Throwable original) {
        Throwable failure = original;
        try {
            if (enumerator != null) {
                enumerator.close();
            }
        } catch (Throwable error) {
            if (failure == null) {
                failure = error;
            } else if (failure != error) {
                failure.addSuppressed(error);
            }
        } finally {
            if (Thread.currentThread() == eventThread) {
                context.dispose();
            }
            discovery.shutdownNow();
            eventLoop.shutdown();
        }
        if (failure == null) {
            termination.complete(null);
        } else {
            termination.completeExceptionally(failure);
        }
    }
}
