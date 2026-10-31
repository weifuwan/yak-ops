package io.yak.ops.flow.runtime.schema;

import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.sink.SchemaEvolutionSink;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeEventType;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot;
import io.yak.ops.flow.runtime.checkpoint.SchemaManagerSnapshotSerializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Serializes runtime schema transitions across Source readers and Sink Writers.
 *
 * <p>A transition validates all Writers, pauses every Source, drains already-emitted records in
 * pipeline order, flushes all Writers, applies the planned target events, updates Writer plans,
 * and only then commits the schema version and resumes the Sources. The checkpoint coordinator
 * uses the same event loop to fence transitions and persist a matching schema version.
 *
 * <p>This is local-runtime coordination. It does not claim distributed exactly-once semantics.
 */
public final class SchemaCoordinator implements AutoCloseable {

    private static final long CLOSE_TIMEOUT_SECONDS = 45;

    private final SchemaManager manager;
    private final MetadataApplier metadataApplier;
    private final SchemaEvolutionSink schemaSink;
    private final boolean allowDropColumns;
    private volatile List<SchemaSourceParticipant> sourceTasks;
    private final List<SchemaChangeParticipant> pipelineParticipants;
    private final List<SchemaChangeParticipant> sinkParticipants;
    private final Consumer<Throwable> onFailure;
    private final ExecutorService eventLoop = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("yak-schema-coordinator-", 0).factory());
    private final Object lock = new Object();
    private final List<PendingChange> deferred = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Throwable fatalFailure;
    private boolean checkpointPauseRequested;
    private boolean checkpointPaused;

    public SchemaCoordinator(
            SchemaManager manager,
            MetadataApplier metadataApplier,
            List<? extends SchemaSourceParticipant> sourceTasks,
            List<? extends SchemaChangeParticipant> pipelineParticipants,
            List<? extends SchemaChangeParticipant> sinkParticipants,
            Consumer<Throwable> onFailure) {
        this(manager, metadataApplier, sourceTasks, pipelineParticipants, sinkParticipants, onFailure, null, false);
    }

    /** Uses the Sink's explicit schema planner; raw MySQL DDL is never passed to the target. */
    public SchemaCoordinator(
            SchemaManager manager,
            MetadataApplier metadataApplier,
            List<? extends SchemaSourceParticipant> sourceTasks,
            List<? extends SchemaChangeParticipant> pipelineParticipants,
            List<? extends SchemaChangeParticipant> sinkParticipants,
            Consumer<Throwable> onFailure,
            SchemaEvolutionSink schemaSink) {
        this(
                manager,
                metadataApplier,
                sourceTasks,
                pipelineParticipants,
                sinkParticipants,
                onFailure,
                schemaSink,
                false);
    }

    /** Allows only explicitly authorized destructive schema transitions in this Job attempt. */
    public SchemaCoordinator(
            SchemaManager manager,
            MetadataApplier metadataApplier,
            List<? extends SchemaSourceParticipant> sourceTasks,
            List<? extends SchemaChangeParticipant> pipelineParticipants,
            List<? extends SchemaChangeParticipant> sinkParticipants,
            Consumer<Throwable> onFailure,
            SchemaEvolutionSink schemaSink,
            boolean allowDropColumns) {
        this.allowDropColumns = allowDropColumns;
        this.manager = Objects.requireNonNull(manager, "manager");
        this.schemaSink = schemaSink;
        this.metadataApplier = Objects.requireNonNull(metadataApplier, "metadataApplier");
        this.sourceTasks = List.copyOf(Objects.requireNonNull(sourceTasks, "sourceTasks"));
        this.pipelineParticipants = List.copyOf(Objects.requireNonNull(pipelineParticipants, "pipelineParticipants"));
        this.sinkParticipants = List.copyOf(Objects.requireNonNull(sinkParticipants, "sinkParticipants"));
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
        if (this.pipelineParticipants.isEmpty() || this.sinkParticipants.isEmpty()) {
            throw new IllegalArgumentException("Schema coordination requires input and Sink tasks");
        }
    }

    /** Binds Source tasks after the coordinator is constructed but before the Job is started. */
    public synchronized void bindSourceTasks(List<? extends SchemaSourceParticipant> tasks) {
        if (!sourceTasks.isEmpty()) {
            throw new IllegalStateException("Source tasks are already bound to SchemaCoordinator");
        }
        List<SchemaSourceParticipant> bound = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        if (bound.isEmpty()) {
            throw new IllegalArgumentException("Schema coordination requires Source tasks");
        }
        for (int index = 0; index < bound.size(); index++) {
            if (bound.get(index).subtaskIndex() != index) {
                throw new IllegalArgumentException("Source schema participants must be ordered by subtask index");
            }
        }
        sourceTasks = bound;
    }

    /**
     * Applies one replay-stable request. The Source must not emit records after the event until
     * the returned stage completes; Source implementations must not block their mailbox thread.
     */
    public CompletionStage<Long> applySchemaChange(SchemaChangeRequest request, int sourceSubtask) {
        Objects.requireNonNull(request, "request");
        if (sourceSubtask < 0
                || sourceSubtask >= sourceTasks.size()
                || sourceTasks.get(sourceSubtask).subtaskIndex() != sourceSubtask) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Source subtask"));
        }
        CompletableFuture<Long> result = new CompletableFuture<>();
        synchronized (lock) {
            if (closed.get()) {
                return CompletableFuture.failedFuture(new IllegalStateException("SchemaCoordinator is closed"));
            }
            if (fatalFailure != null) {
                return CompletableFuture.failedFuture(fatalFailure);
            }
            PendingChange pending = new PendingChange(request, sourceSubtask, result);
            if (checkpointPauseRequested || checkpointPaused) {
                deferred.add(pending);
            } else {
                submit(() -> process(pending));
            }
        }
        return result;
    }

    /** Plans a source change synchronously without I/O, then queues the normal coordination protocol. */
    public CompletionStage<Long> applySourceSchemaChange(SourceSchemaChange change, int sourceSubtask) {
        Objects.requireNonNull(change, "change");
        if (schemaSink == null) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("Runtime schema evolution requires a Sink schema planner"));
        }
        try {
            return applySchemaChange(schemaSink.planSchemaChange(change, allowDropColumns), sourceSubtask);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /** Returns the committed source schema for restoring the Debezium converter at the same offset. */
    public java.util.Optional<Schema> sourceSchema(TableId sourceTable) {
        return manager.sourceState(sourceTable).map(SchemaManagerSnapshot.TableState::sourceSchema);
    }

    /** Returns the version persisted with this source table's current schema and offset. */
    public long sourceSchemaVersion(TableId sourceTable) {
        return manager.sourceState(sourceTable)
                .map(SchemaManagerSnapshot.TableState::version)
                .orElse(0L);
    }

    /** Fences new schema requests and waits for the active transition to finish. */
    public CompletableFuture<Void> pauseForCheckpoint() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        synchronized (lock) {
            if (closed.get()) {
                return CompletableFuture.failedFuture(new IllegalStateException("SchemaCoordinator is closed"));
            }
            if (checkpointPauseRequested || checkpointPaused) {
                return CompletableFuture.failedFuture(new IllegalStateException("SchemaCoordinator is already paused"));
            }
            checkpointPauseRequested = true;
            submit(
                    () -> {
                        synchronized (lock) {
                            checkpointPaused = true;
                            checkpointPauseRequested = false;
                        }
                        result.complete(null);
                    },
                    result);
        }
        return result;
    }

    /** Captures only committed versions; caller must first await pauseForCheckpoint(). */
    public CheckpointSnapshot.SerializedState snapshotState() throws IOException {
        synchronized (lock) {
            if (!checkpointPaused || checkpointPauseRequested) {
                throw new IllegalStateException("SchemaCoordinator must be paused before checkpoint snapshot");
            }
        }
        SchemaManagerSnapshotSerializer serializer = new SchemaManagerSnapshotSerializer();
        return new CheckpointSnapshot.SerializedState(
                serializer.getVersion(), serializer.serialize(manager.snapshot()));
    }

    /** Restores committed source/target schemas before any task or SourceReader is started. */
    public static SchemaManager restoreManager(CheckpointSnapshot.SerializedState state) throws IOException {
        SchemaManager manager = new SchemaManager();
        if (state != null) {
            manager.restore(new SchemaManagerSnapshotSerializer().deserialize(state.version(), state.bytes()));
        }
        return manager;
    }

    /** Releases the checkpoint fence and schedules requests that arrived while it was held. */
    public CompletableFuture<Void> resumeAfterCheckpoint() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        synchronized (lock) {
            if (closed.get()) {
                return CompletableFuture.failedFuture(new IllegalStateException("SchemaCoordinator is closed"));
            }
            submit(
                    () -> {
                        List<PendingChange> pendingChanges;
                        synchronized (lock) {
                            checkpointPaused = false;
                            checkpointPauseRequested = false;
                            pendingChanges = List.copyOf(deferred);
                            deferred.clear();
                        }
                        result.complete(null);
                        for (PendingChange pending : pendingChanges) {
                            submit(() -> process(pending), pending.result());
                        }
                    },
                    result);
        }
        return result;
    }

    public long currentVersion(io.yak.ops.core.data.TableId targetTable) {
        return manager.currentVersion(targetTable);
    }

    private void process(PendingChange pending) {
        SchemaChangeRequest request = pending.request();
        boolean begun = false;
        boolean ddlStarted = false;
        try {
            ensureHealthy();
            SchemaManager.Change change = manager.begin(request);
            if (change.duplicate()) {
                pending.result().complete(change.version());
                return;
            }
            begun = true;

            // Validate all immutable Writer replacements before pausing input or mutating the target.
            for (SchemaChangeParticipant participant : sinkParticipants) {
                await(participant.validateSchemaChange(request));
            }

            List<SchemaSourceParticipant> paused = new ArrayList<>();
            for (SchemaSourceParticipant source : sourceTasks) {
                await(source.pauseForSchemaChange());
                paused.add(source);
            }

            // Drain every upstream stage before its downstream stage. This preserves channel order
            // without changing the connector's source/binlog implementation.
            for (SchemaChangeParticipant participant : pipelineParticipants) {
                await(participant.drainForSchemaChange());
            }
            for (SchemaChangeParticipant participant : sinkParticipants) {
                await(participant.prepareSchemaChange(request));
            }

            Set<SchemaChangeEventType> accepted = new HashSet<>();
            for (SchemaChangeEvent event : request.targetChanges()) {
                accepted.add(event.getType());
            }
            metadataApplier.setAcceptedSchemaEvolutionTypes(Set.copyOf(accepted));
            ddlStarted = true;
            for (SchemaChangeEvent event : request.targetChanges()) {
                ensureHealthy();
                metadataApplier.applySchemaChange(event);
            }

            for (SchemaChangeParticipant participant : sinkParticipants) {
                await(participant.commitSchemaChange(request, change.version()));
            }
            if (schemaSink != null) {
                schemaSink.schemaChangeCompleted(request);
            }
            long committedVersion = manager.complete(request);
            if (committedVersion != change.version()) {
                throw new IllegalStateException("Committed schema version changed during coordination");
            }
            for (SchemaSourceParticipant source : paused) {
                await(source.resumeAfterSchemaChange());
            }
            pending.result().complete(committedVersion);
        } catch (Throwable error) {
            Throwable failure = unwrap(error);
            if (begun && !ddlStarted) {
                manager.abort(request);
            }
            fail(failure);
            pending.result().completeExceptionally(failure);
        }
    }

    private void fail(Throwable failure) {
        if (fatalFailure == null) {
            synchronized (lock) {
                if (fatalFailure == null) {
                    fatalFailure = failure;
                }
            }
            try {
                onFailure.accept(failure);
            } catch (Throwable callbackFailure) {
                if (callbackFailure != failure) failure.addSuppressed(callbackFailure);
            }
        }
    }

    private void ensureHealthy() {
        if (closed.get()) throw new IllegalStateException("SchemaCoordinator is closed");
        Throwable failure = fatalFailure;
        if (failure != null) throw new IllegalStateException("SchemaCoordinator has failed", failure);
    }

    private void submit(Runnable action) {
        submit(action, null);
    }

    private void submit(Runnable action, CompletableFuture<?> failureTarget) {
        try {
            eventLoop.execute(() -> {
                try {
                    ensureHealthy();
                    action.run();
                } catch (Throwable failure) {
                    if (failureTarget != null) failureTarget.completeExceptionally(failure);
                    else fail(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            if (failureTarget != null) failureTarget.completeExceptionally(failure);
            else fail(failure);
        }
    }

    private static void await(CompletionStage<?> stage) throws Exception {
        try {
            stage.toCompletableFuture().get(60, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            Throwable cause = unwrap(failure);
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable result = failure;
        while ((result instanceof ExecutionException || result instanceof java.util.concurrent.CompletionException)
                && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    @Override
    public void close() throws Exception {
        if (!closed.compareAndSet(false, true)) return;
        List<PendingChange> waiting;
        synchronized (lock) {
            waiting = List.copyOf(deferred);
            deferred.clear();
        }
        IllegalStateException closing =
                new IllegalStateException("SchemaCoordinator closed before schema change completed");
        waiting.forEach(pending -> pending.result().completeExceptionally(closing));
        eventLoop.shutdown();
        if (!eventLoop.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            try {
                metadataApplier.cancel();
            } catch (Exception cancelFailure) {
                closing.addSuppressed(cancelFailure);
            }
            eventLoop.shutdownNow();
            if (!eventLoop.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("SchemaCoordinator could not stop active schema work", closing);
            }
        }
        metadataApplier.close();
    }

    private record PendingChange(SchemaChangeRequest request, int sourceSubtask, CompletableFuture<Long> result) {
        private PendingChange {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(result, "result");
        }
    }
}
