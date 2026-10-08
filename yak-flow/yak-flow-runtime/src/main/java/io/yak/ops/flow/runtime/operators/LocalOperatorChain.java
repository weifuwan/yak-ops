package io.yak.ops.flow.runtime.operators;

import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.operators.OneInputOperator;
import io.yak.ops.core.graph.StreamNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 单个 Source StreamTask 内同步执行的零个或多个 OneInputOperator 和一个 SinkWriter。
 *
 * <p>只在所属 Task Mailbox 线程创建、打开、处理、finish、flush 和关闭运行实例。
 * 本类不建立额外线程或 Channel，只有线性单并行拓扑才能使用。
 */
public final class LocalOperatorChain implements ReaderOutput<Object>, AutoCloseable {

    private final List<StreamNode> operatorNodes;
    private final StreamNode sinkNode;
    private final List<OneInputOperator<Object, Object>> operators = new ArrayList<>();
    private SinkWriter<Object> writer;
    private boolean opened;
    private boolean finished;
    private boolean closed;

    public LocalOperatorChain(List<StreamNode> operatorNodes, StreamNode sinkNode) {
        Objects.requireNonNull(operatorNodes, "operatorNodes 不能为空");
        for (StreamNode node : operatorNodes) {
            if (node == null || !node.isOperator()) {
                throw new IllegalArgumentException("Operator Chain 只接受 OneInputOperator 节点");
            }
        }
        this.operatorNodes = List.copyOf(operatorNodes);
        this.sinkNode = Objects.requireNonNull(sinkNode, "sinkNode 不能为空");
        if (!sinkNode.isSink()) {
            throw new IllegalArgumentException("Operator Chain 需要 Sink 终点");
        }
    }

    /** 先创建下游 Writer，再创建并按下游到上游顺序打开 Operator。 */
    @SuppressWarnings("unchecked")
    public void open() throws Exception {
        if (opened || closed) {
            throw new IllegalStateException("Operator Chain 已经打开或关闭");
        }
        opened = true;
        writer = (SinkWriter<Object>) Objects.requireNonNull(
                sinkNode.getSink().orElseThrow().createWriter(), "Sink 返回了空 Writer");
        for (StreamNode node : operatorNodes) {
            operators.add((OneInputOperator<Object, Object>) Objects.requireNonNull(
                    node.getOperatorFactory().orElseThrow().createOperator(), "Operator 工厂返回了 null"));
        }
        for (int i = operators.size() - 1; i >= 0; i--) {
            operators.get(i).open();
        }
    }

    /** Reader 和每个 Operator 通过同步 Collector 直接调用下一环；异常原样向上游传递。 */
    @Override
    public void collect(Object record) throws Exception {
        if (!opened || finished || closed) {
            throw new IllegalStateException("Operator Chain 不是可写状态");
        }
        forward(0, record);
    }

    private void forward(int index, Object record) throws Exception {
        if (index == operators.size()) {
            writer.write(record);
            return;
        }
        operators.get(index).processElement(record, output -> forward(index + 1, output));
    }

    /**
     * 只有正常 EOF 才按上游到下游顺序通知 Operator.finish，最后调用 Sink.flush(true)。
     * 失败和取消时只释放资源，不冒充正常输入结束。
     */
    public void finish() throws Exception {
        if (!opened || finished || closed) {
            throw new IllegalStateException("Operator Chain 不能重复 finish");
        }
        for (int i = 0; i < operators.size(); i++) {
            int next = i + 1;
            operators.get(i).finish(output -> forward(next, output));
        }
        writer.flush(true);
        finished = true;
    }

    /** 关闭所有已创建实例；即使部分 Operator 初始化或关闭失败，也尽力释放剩余资源。 */
    @Override
    public void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        Throwable failure = null;
        for (OneInputOperator<Object, Object> operator : operators) {
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
        if (writer != null) {
            try {
                writer.close();
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
