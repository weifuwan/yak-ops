package io.yak.ops.flow.runtime.tasks;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.graph.StreamNode;
import io.yak.ops.flow.runtime.io.RecordWriterOutput;
import io.yak.ops.flow.runtime.io.StreamTaskInput;
import io.yak.ops.flow.runtime.io.StreamTaskNetworkInput;
import io.yak.ops.flow.runtime.io.partition.InputGate;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import io.yak.ops.flow.runtime.operators.StreamOperator;
import io.yak.ops.flow.runtime.operators.sink.SinkWriterOperator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * The common physical one-input task for transforming operators and SinkWriterOperator.
 * Every callback, including Sink checkpoint flush, runs on this Task's mailbox thread.
 */
public final class OneInputStreamTask extends StreamTask {

    private final StreamNode node;
    private final StreamTaskInput<Object> input;
    private final RecordWriterOutput<Object> output;
    private OneInputStreamOperator<Object, Object> operator;
    private SinkWriterOperator<Object> sinkOperator;
    private StreamOperator activeOperator;

    /** Non-sink operator task with a producer-owned downstream ResultPartition. */
    public OneInputStreamTask(
            StreamNode node, TaskEnvironment environment, InputGate<Object> inputGate,
            RecordWriterOutput<Object> output) {
        this(node, environment, inputGate, output, false);
    }

    /** Sink task uses the same StreamTask lifecycle but has no downstream output partition. */
    public OneInputStreamTask(StreamNode node, TaskEnvironment environment, InputGate<Object> inputGate) {
        this(node, environment, inputGate, null, true);
    }

    private OneInputStreamTask(
            StreamNode node, TaskEnvironment environment, InputGate<Object> inputGate,
            RecordWriterOutput<Object> output, boolean sinkTask) {
        super(environment);
        this.node = Objects.requireNonNull(node, "node");
        if ((sinkTask && !node.isSink()) || (!sinkTask && !node.isOperator())
                || node.getId() != taskInfo().operatorId()
                || node.getParallelism() != taskInfo().parallelism()) {
            throw new IllegalArgumentException("RuntimeTaskInfo does not match its physical operator");
        }
        this.input = new StreamTaskNetworkInput<>(Objects.requireNonNull(inputGate, "inputGate"));
        this.output = sinkTask ? null : Objects.requireNonNull(output, "output");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void openTask() throws Exception {
        if (node.isSink()) {
            sinkOperator = new SinkWriterOperator<>(
                    (Sink<Object>) node.getSink().orElseThrow(), taskEnvironment());
            activeOperator = sinkOperator;
        } else {
            operator = (OneInputStreamOperator<Object, Object>) Objects.requireNonNull(
                    node.getOperatorFactory().orElseThrow().createOperator(), "Operator factory returned null");
            activeOperator = operator;
        }
        activeOperator.open();
    }

    @Override
    protected InputStatus processInput() throws Exception {
        if (sinkOperator != null) {
            return input.emitNext(value -> sinkOperator.processElement(value, ignored -> {}));
        }
        return input.emitNext(value -> operator.processElement(value, output::collect));
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

    /** The existing Source→Sink quiescent checkpoint coordinates this call after gate drain. */
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
        if (activeOperator != null) {
            activeOperator.close();
        }
    }
}
