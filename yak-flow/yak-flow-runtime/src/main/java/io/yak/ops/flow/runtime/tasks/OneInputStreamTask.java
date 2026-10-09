package io.yak.ops.flow.runtime.tasks;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.InputStatus;
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
import io.yak.ops.flow.runtime.state.OperatorStateBackend;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single-input StreamTask. Data, state callbacks and barrier forwarding run on its mailbox.
 * Each barrier is acknowledged only after input alignment, local snapshot and output propagation.
 */
public final class OneInputStreamTask extends StreamTask {

    private final StreamNode node;
    private final StreamTaskNetworkInput<Object> input;
    private final RecordWriterOutput<Object> output;
    private final Map<String, CheckpointSnapshot.SerializedState> restoredState;
    private final boolean keyedInput;
    private final Map<Long, CompletableFuture<Map<String, CheckpointSnapshot.SerializedState>>> pending =
            new ConcurrentHashMap<>();
    private OneInputStreamOperator<Object, Object> operator;
    private SinkWriterOperator<Object> sinkOperator;
    private StreamOperator activeOperator;
    private OperatorStateBackend stateBackend;

    public OneInputStreamTask(
            StreamNode node, TaskEnvironment environment, InputGate<Object> inputGate,
            RecordWriterOutput<Object> output) {
        this(node, environment, inputGate, output, Map.of(), false);
    }

    public OneInputStreamTask(StreamNode node, TaskEnvironment environment, InputGate<Object> inputGate) {
        this(node, environment, inputGate, null, Map.of(), false);
    }

    /** Used by deployment to bind the state of this stable operator UID / subtask. */
    public OneInputStreamTask(
            StreamNode node, TaskEnvironment environment, InputGate<Object> inputGate,
            RecordWriterOutput<Object> output,
            Map<String, CheckpointSnapshot.SerializedState> restoredState, boolean keyedInput) {
        super(environment);
        this.node = Objects.requireNonNull(node, "node");
        if ((!node.isSink() && !node.isOperator()) || node.getId() != taskInfo().operatorId()
                || node.getParallelism() != taskInfo().parallelism()
                || (node.isSink() && output != null) || (node.isOperator() && output == null)) {
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

    /** Register before a source is allowed to emit the barrier, avoiding an ACK registration race. */
    public CompletableFuture<Map<String, CheckpointSnapshot.SerializedState>> expectCheckpoint(long id) {
        if (id <= 0 || completionFuture().isDone()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Task cannot accept checkpoint"));
        }
        CompletableFuture<Map<String, CheckpointSnapshot.SerializedState>> future = new CompletableFuture<>();
        if (pending.putIfAbsent(id, future) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Duplicate checkpoint registration"));
        }
        return future;
    }

    public void abortCheckpoint(long id, Throwable cause) {
        CompletableFuture<Map<String, CheckpointSnapshot.SerializedState>> future = pending.remove(id);
        if (future != null) {
            future.completeExceptionally(cause);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void openTask() throws Exception {
        stateBackend = new OperatorStateBackend(restoredState, taskInfo(), keyedInput);
        if (node.isSink()) {
            sinkOperator = new SinkWriterOperator<>(
                    (Sink<Object>) node.getSink().orElseThrow(), taskEnvironment());
            activeOperator = sinkOperator;
        } else {
            operator = (OneInputStreamOperator<Object, Object>) Objects.requireNonNull(
                    node.getOperatorFactory().orElseThrow().createOperator(), "Operator factory returned null");
            activeOperator = operator;
        }
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
            return input.emitNext(value -> sinkOperator.processElement(value, ignored -> {}),
                    this::onCheckpointBarrier);
        }
        return input.emitNext(value -> operator.processElement(value, output::collect),
                this::onCheckpointBarrier);
    }

    private void onCheckpointBarrier(long checkpointId) throws Exception {
        CompletableFuture<Map<String, CheckpointSnapshot.SerializedState>> acknowledgement =
                pending.remove(checkpointId);
        if (acknowledgement == null) {
            throw new IllegalStateException("Unexpected or aborted barrier: " + checkpointId);
        }
        try {
            if (activeOperator instanceof CheckpointedStreamOperator checkpointed) {
                checkpointed.snapshotState(checkpointId, stateBackend);
            }
            Map<String, CheckpointSnapshot.SerializedState> snapshot = stateBackend.snapshot();
            if (output != null) {
                output.broadcastBarrier(checkpointId);
            }
            acknowledgement.complete(snapshot);
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
            output.finish();
        }
    }

    /** Backwards-compatible direct flush API for callers not yet using aligned barriers. */
    public CompletableFuture<Void> flushForCheckpoint(long checkpointId) {
        if (checkpointId <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("checkpointId must be positive"));
        }
        if (!node.isSink()) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("Only a SinkWriterOperator may flush for checkpoint"));
        }
        return submitMailbox(() -> {
            sinkOperator.flushForCheckpoint(checkpointId);
            return null;
        });
    }

    @Override
    protected void closeTask() throws Exception {
        try {
            if (activeOperator != null) {
                activeOperator.close();
            }
        } finally {
            pending.forEach((id, future) ->
                    future.completeExceptionally(new IllegalStateException("Task ended before checkpoint ACK")));
            pending.clear();
        }
    }
}
