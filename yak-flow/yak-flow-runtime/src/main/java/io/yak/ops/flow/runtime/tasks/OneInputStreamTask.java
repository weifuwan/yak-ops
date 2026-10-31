package io.yak.ops.flow.runtime.tasks;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.flow.runtime.checkpoint.CheckpointDeclinedException;
import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.graph.StreamNode;
import io.yak.ops.flow.runtime.io.RecordWriterOutput;
import io.yak.ops.flow.runtime.io.StreamTaskNetworkInput;
import io.yak.ops.flow.runtime.io.partition.InputGate;
import io.yak.ops.flow.runtime.operators.CheckpointedStreamOperator;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import io.yak.ops.flow.runtime.operators.StreamOperator;
import io.yak.ops.flow.runtime.operators.sink.SinkWriterOperator;
import io.yak.ops.flow.runtime.schema.SchemaChangeParticipant;
import io.yak.ops.flow.runtime.state.OperatorStateBackend;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single-input StreamTask. Data, state callbacks and barrier forwarding run on its mailbox.
 * Each barrier is acknowledged only after input alignment, local snapshot and output propagation.
 */
public final class OneInputStreamTask extends StreamTask implements SchemaChangeParticipant {

    private final StreamNode node;
    private final StreamTaskNetworkInput<Object> input;
    private final RecordWriterOutput<Object> output;
    private final Map<String, CheckpointSnapshot.SerializedState> restoredState;
    private final boolean keyedInput;
    private volatile long lastDeclinedCheckpointId;
    private CompletableFuture<Void> endOfDataAcknowledged = CompletableFuture.completedFuture(null);
    private final InputGate.BarrierHandler barrierHandler = new InputGate.BarrierHandler() {
        @Override
        public void onBarrier(long checkpointId) throws Exception {
            onCheckpointBarrier(checkpointId);
        }

        @Override
        public void onEndOfData() throws Exception {
            endData();
        }

        @Override
        public void onCheckpointDeclined(long checkpointId) {
            lastDeclinedCheckpointId = Math.max(lastDeclinedCheckpointId, checkpointId);
            abortCheckpoint(
                    checkpointId,
                    new CheckpointDeclinedException(checkpointId, "a producer ended before barrier alignment"));
        }
    };
    private final Map<Long, CompletableFuture<CheckpointState>> pending = new ConcurrentHashMap<>();

    /** Operator state and its finished flag are captured in the same mailbox barrier cut. */
    public record CheckpointState(Map<String, CheckpointSnapshot.SerializedState> operatorStates, boolean finished) {
        public CheckpointState {
            operatorStates = Map.copyOf(Objects.requireNonNull(operatorStates, "operatorStates"));
            if (finished && !operatorStates.isEmpty()) {
                throw new IllegalArgumentException("Finished operator cannot snapshot mutable state");
            }
        }
    }

    private OneInputStreamOperator<Object, Object> operator;
    private volatile SinkWriterOperator<Object> sinkOperator;
    private StreamOperator activeOperator;
    private OperatorStateBackend stateBackend;

    public OneInputStreamTask(
            StreamNode node,
            TaskEnvironment environment,
            InputGate<Object> inputGate,
            RecordWriterOutput<Object> output) {
        this(node, environment, inputGate, output, Map.of(), false);
    }

    public OneInputStreamTask(StreamNode node, TaskEnvironment environment, InputGate<Object> inputGate) {
        this(node, environment, inputGate, null, Map.of(), false);
    }

    /** Used by deployment to bind the state of this stable operator UID / subtask. */
    public OneInputStreamTask(
            StreamNode node,
            TaskEnvironment environment,
            InputGate<Object> inputGate,
            RecordWriterOutput<Object> output,
            Map<String, CheckpointSnapshot.SerializedState> restoredState,
            boolean keyedInput) {
        super(environment);
        this.node = Objects.requireNonNull(node, "node");
        if ((!node.isSink() && !node.isOperator())
                || node.getId() != taskInfo().operatorId()
                || node.getParallelism() != taskInfo().parallelism()
                || (node.isSink() && output != null)
                || (node.isOperator() && output == null)) {
            throw new IllegalArgumentException("RuntimeTaskInfo does not match its physical operator");
        }
        this.input = new StreamTaskNetworkInput<>(Objects.requireNonNull(inputGate, "inputGate"));
        this.output = output;
        this.restoredState = Map.copyOf(Objects.requireNonNull(restoredState, "restoredState"));
        this.keyedInput = keyedInput;
    }

    public CheckpointSnapshot.OperatorSubtask checkpointIdentity() {
        return new CheckpointSnapshot.OperatorSubtask(node.getUid(), taskInfo().subtaskIndex());
    }

    public boolean isSinkTask() {
        return node.isSink();
    }

    @Override
    public CompletableFuture<Void> validateSchemaChange(SchemaChangeRequest request) {
        Objects.requireNonNull(request, "request");
        return submitMailbox(() -> {
            if (sinkOperator == null) {
                throw new UnsupportedOperationException("Only Sink tasks can adopt a runtime schema change");
            }
            sinkOperator.validateSchemaChange(request);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> drainForSchemaChange() {
        return submitMailbox(() -> {
            while (true) {
                InputStatus status;
                if (sinkOperator != null) {
                    status = input.emitNext(value -> sinkOperator.processElement(value, ignored -> {}), barrierHandler);
                } else {
                    status = input.emitNext(value -> operator.processElement(value, output::collect), barrierHandler);
                }
                if (status == InputStatus.MORE_AVAILABLE) continue;
                if (status == InputStatus.NOTHING_AVAILABLE) return null;
                throw new IllegalStateException("Schema change cannot drain a completed input task");
            }
        });
    }

    @Override
    public CompletableFuture<Void> prepareSchemaChange(SchemaChangeRequest request) {
        Objects.requireNonNull(request, "request");
        return submitMailbox(() -> {
            if (sinkOperator == null) {
                throw new UnsupportedOperationException("Only Sink tasks can flush for a schema change");
            }
            sinkOperator.prepareSchemaChange(request);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> commitSchemaChange(SchemaChangeRequest request, long schemaVersion) {
        Objects.requireNonNull(request, "request");
        return submitMailbox(() -> {
            if (sinkOperator == null) {
                throw new UnsupportedOperationException("Only Sink tasks can adopt a schema change");
            }
            sinkOperator.commitSchemaChange(request, schemaVersion);
            return null;
        });
    }

    /** Register before a source is allowed to emit the barrier, avoiding an ACK registration race. */
    public CompletableFuture<CheckpointState> expectCheckpoint(long id) {
        if (id <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid checkpoint ID"));
        }
        if (completionFuture().isDone()) {
            return CompletableFuture.failedFuture(new CheckpointDeclinedException(id, "input already finished"));
        }
        CompletableFuture<CheckpointState> future = new CompletableFuture<>();
        if (pending.putIfAbsent(id, future) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Duplicate checkpoint registration"));
        }
        return future;
    }

    /** Commit notification is serialized on the owning Sink mailbox. */
    public CompletableFuture<Void> notifyCheckpointComplete(long checkpointId) {
        return submitMailbox(() -> {
            if (sinkOperator != null) sinkOperator.notifyCheckpointComplete(checkpointId);
            return null;
        });
    }

    public void abortCheckpoint(long id, Throwable cause) {
        CompletableFuture<CheckpointState> future = pending.remove(id);
        if (future != null) {
            future.completeExceptionally(cause);
        }
    }

    /** Cancel only the in-flight checkpoint; do not interrupt an otherwise healthy StreamTask. */
    public void declineCheckpoint(long checkpointId, Throwable cause) {
        lastDeclinedCheckpointId = Math.max(lastDeclinedCheckpointId, checkpointId);
        input.declineCheckpoint(checkpointId);
        abortCheckpoint(checkpointId, cause);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void openTask() throws Exception {
        stateBackend = new OperatorStateBackend(restoredState, taskInfo(), keyedInput);
        if (node.isSink()) {
            sinkOperator =
                    new SinkWriterOperator<>((Sink<Object>) node.getSink().orElseThrow(), taskEnvironment());
            activeOperator = sinkOperator;
        } else {
            operator = (OneInputStreamOperator<Object, Object>) Objects.requireNonNull(
                    node.getOperatorFactory().orElseThrow().createOperator(), "Operator factory returned null");
            activeOperator = operator;
        }
        activeOperator.setup(taskInfo());
        if (activeOperator instanceof CheckpointedStreamOperator checkpointed) {
            checkpointed.initializeState(stateBackend);
        } else if (!restoredState.isEmpty()) {
            throw new IllegalStateException("Non-checkpointed operator has saved state: " + node.getUid());
        }
        activeOperator.open();
    }

    @Override
    protected InputStatus processInput() throws Exception {
        if (sinkOperator != null) {
            return input.emitNext(value -> sinkOperator.processElement(value, ignored -> {}), barrierHandler);
        }
        return input.emitNext(value -> operator.processElement(value, output::collect), barrierHandler);
    }

    /** Starts a checkpoint at an input Task with no running upstream producer. */
    public CompletableFuture<Void> triggerCheckpoint(long checkpointId) {
        return submitMailbox(() -> {
            onCheckpointBarrier(checkpointId);
            return null;
        });
    }

    private void onCheckpointBarrier(long checkpointId) throws Exception {
        CompletableFuture<CheckpointState> acknowledgement = pending.remove(checkpointId);
        if (acknowledgement == null) {
            if (checkpointId <= lastDeclinedCheckpointId) {
                return; // A late barrier from an already declined checkpoint is not a task failure.
            }
            throw new IllegalStateException("Unexpected checkpoint barrier: " + checkpointId);
        }
        try {
            boolean finished = hasReachedEndOfData();
            Map<String, CheckpointSnapshot.SerializedState> snapshot = Map.of();
            if (!finished) {
                if (activeOperator instanceof CheckpointedStreamOperator checkpointed) {
                    checkpointed.snapshotState(checkpointId, stateBackend);
                }
                snapshot = stateBackend.snapshot();
            }
            if (output != null) {
                output.broadcastBarrier(checkpointId);
            }
            acknowledgement.complete(new CheckpointState(snapshot, finished));
        } catch (Exception | Error failure) {
            acknowledgement.completeExceptionally(failure);
            throw failure;
        }
    }

    @Override
    protected CompletableFuture<Void> getAvailableFuture() {
        return input.getAvailableFuture();
    }

    @Override
    protected void finishTask() throws Exception {
        if (sinkOperator != null) {
            sinkOperator.finish();
        } else {
            operator.finish(output::collect);
            endOfDataAcknowledged = output.endData();
        }
    }

    @Override
    protected CompletableFuture<Void> endOfDataAcknowledgement() {
        return endOfDataAcknowledged;
    }

    @Override
    protected void finishOutput() {
        if (output != null) {
            output.finish();
        }
    }

    @Override
    protected void onCancellationRequested() {
        SinkWriterOperator<Object> active = sinkOperator;
        if (active != null) {
            active.requestCancel();
        }
    }

    @Override
    protected void closeTask() throws Exception {
        try {
            if (activeOperator != null) {
                activeOperator.close();
            }
        } finally {
            pending.forEach((id, future) -> future.completeExceptionally(
                    new CheckpointDeclinedException(id, "input ended before checkpoint ACK")));
            pending.clear();
        }
    }
}
