package io.yak.ops.flow.runtime.operators;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.graph.StreamNode;
import io.yak.ops.flow.runtime.operators.sink.SinkWriterOperator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Inline StreamOperator chain for a single-parallelism Source→Operator*→Sink job.
 *
 * <p>The same SinkWriterOperator handles writer lifecycle in an inline chain and in a separate
 * OneInputStreamTask. Open downstream first, finish upstream first, close all resources on error.
 */
public final class OperatorChain implements ReaderOutput<Object>, AutoCloseable {

    private final List<StreamNode> operatorNodes;
    private final StreamNode sinkNode;
    private final List<OneInputStreamOperator<Object, Object>> operators = new ArrayList<>();
    private SinkWriterOperator<Object> sinkOperator;
    private boolean opened;
    private boolean finished;
    private boolean closed;

    public OperatorChain(List<StreamNode> operatorNodes, StreamNode sinkNode) {
        Objects.requireNonNull(operatorNodes, "operatorNodes");
        for (StreamNode node : operatorNodes) {
            if (node == null || !node.isOperator()) {
                throw new IllegalArgumentException("OperatorChain only accepts OneInput operators");
            }
        }
        this.operatorNodes = List.copyOf(operatorNodes);
        this.sinkNode = Objects.requireNonNull(sinkNode, "sinkNode");
        if (!sinkNode.isSink()) {
            throw new IllegalArgumentException("OperatorChain requires a Sink");
        }
    }

    /** Called on the owning SourceStreamTask mailbox thread, before opening its SourceReader. */
    @SuppressWarnings("unchecked")
    public void open(TaskEnvironment sourceEnvironment) throws Exception {
        if (opened || closed) {
            throw new IllegalStateException("OperatorChain cannot be opened again");
        }
        Objects.requireNonNull(sourceEnvironment, "sourceEnvironment");
        opened = true;
        RuntimeTaskInfo sourceTask = sourceEnvironment.taskInfo();
        // Chained operators still have their own stable logical identities within the physical Task.
        TaskEnvironment sinkEnvironment = new TaskEnvironment(
                new RuntimeTaskInfo(
                        sourceTask.jobID(),
                        sinkNode.getId(),
                        sourceTask.subtaskIndex(),
                        sinkNode.getParallelism(),
                        sourceTask.attemptNumber(),
                        sourceTask.maxParallelism()),
                sourceEnvironment.configuration());
        sinkOperator =
                new SinkWriterOperator<>((Sink<Object>) sinkNode.getSink().orElseThrow(), sinkEnvironment);
        sinkOperator.open();
        for (StreamNode node : operatorNodes) {
            operators.add((OneInputStreamOperator<Object, Object>) Objects.requireNonNull(
                    node.getOperatorFactory().orElseThrow().createOperator(), "Operator factory returned null"));
        }
        for (int i = operators.size() - 1; i >= 0; i--) {
            operators.get(i).open();
        }
    }

    @Override
    public void collect(Object value) throws Exception {
        if (!opened || finished || closed) {
            throw new IllegalStateException("OperatorChain is not accepting records");
        }
        forward(0, value);
    }

    private void forward(int index, Object record) throws Exception {
        if (index == operators.size()) {
            sinkOperator.processElement(record, ignored -> {});
            return;
        }
        operators.get(index).processElement(record, output -> forward(index + 1, output));
    }

    public void finish() throws Exception {
        if (!opened || finished || closed) {
            throw new IllegalStateException("OperatorChain cannot finish again");
        }
        for (int i = 0; i < operators.size(); i++) {
            int next = i + 1;
            operators.get(i).finish(output -> forward(next, output));
        }
        sinkOperator.finish();
        finished = true;
    }

    @Override
    public void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        Throwable failure = null;
        for (OneInputStreamOperator<Object, Object> operator : operators) {
            try {
                operator.close();
            } catch (Throwable error) {
                if (failure == null) {
                    failure = error;
                } else if (failure != error) {
                    failure.addSuppressed(error);
                }
            }
        }
        if (sinkOperator != null) {
            try {
                sinkOperator.close();
            } catch (Throwable error) {
                if (failure == null) {
                    failure = error;
                } else if (failure != error) {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
    }
}
