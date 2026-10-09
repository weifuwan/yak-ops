package io.yak.ops.flow.runtime.tasks;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.graph.StreamNode;
import io.yak.ops.flow.runtime.io.RecordChannel;
import io.yak.ops.flow.runtime.io.RecordRouter;
import io.yak.ops.flow.runtime.operators.OneInputOperator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 单输入 Operator 的独立本地 Task。每次从有界 Channel 读取一条记录，并同步转发至下游。
 * Operator 实例仅由所属 Task Mailbox 创建、调用和关闭。
 */
public final class OneInputStreamTask extends StreamTask {

    private final StreamNode node;
    private final RecordChannel<Object> input;
    private final RecordRouter<Object> output;
    private OneInputOperator<Object, Object> operator;

    public OneInputStreamTask(
            StreamNode node, TaskEnvironment environment, RecordChannel<Object> input,
            RecordRouter<Object> output) {
        super(environment);
        this.node = Objects.requireNonNull(node, "node 不能为空");
        if (!node.isOperator() || node.getId() != taskInfo().operatorId()
                || node.getParallelism() != taskInfo().parallelism()) {
            throw new IllegalArgumentException("RuntimeTaskInfo 与 OneInputOperator 节点不一致");
        }
        this.input = Objects.requireNonNull(input, "input 不能为空");
        this.output = Objects.requireNonNull(output, "output 不能为空");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void openTask() throws Exception {
        operator = (OneInputOperator<Object, Object>) Objects.requireNonNull(
                node.getOperatorFactory().orElseThrow().createOperator(), "Operator 工厂返回 null");
        operator.open();
    }

    @Override
    protected InputStatus processInput() throws Exception {
        return input.emitNext(record -> operator.processElement(record, output::collect));
    }

    @Override
    protected CompletableFuture<Void> getAvailableFuture() {
        return input.isAvailable();
    }

    @Override
    protected void finishTask() throws Exception {
        operator.finish(output::collect);
        output.finish();
    }

    @Override
    protected void closeTask() throws Exception {
        if (operator != null) {
            operator.close();
        }
    }
}
