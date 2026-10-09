package io.yak.ops.flow.runtime.tasks;

import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.graph.StreamNode;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.io.LocalChannel;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** SinkWriter 的独立本地 Task。一个并行子任务拥有一个 Writer 与一个有界输入 Channel。 */
public final class SinkOperatorStreamTask extends StreamTask {

    private final StreamNode node;
    private final LocalChannel<Object> input;
    private SinkWriter<Object> writer;

    public SinkOperatorStreamTask(StreamNode node, TaskEnvironment environment, LocalChannel<Object> input) {
        super(environment);
        this.node = Objects.requireNonNull(node, "node 不能为空");
        if (!node.isSink() || node.getId() != taskInfo().operatorId()
                || node.getParallelism() != taskInfo().parallelism()) {
            throw new IllegalArgumentException("TaskInfo 与 Sink 节点不一致");
        }
        this.input = Objects.requireNonNull(input, "input 不能为空");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void openTask() throws Exception {
        writer = (SinkWriter<Object>) Objects.requireNonNull(
                node.getSink().orElseThrow().createWriter(), "Sink 返回 null Writer");
    }

    @Override
    protected InputStatus processInput() throws Exception {
        return input.emitNext(writer::write);
    }

    @Override
    protected CompletableFuture<Void> getAvailableFuture() {
        return input.isAvailable();
    }

    /** Source 已停止输出，所有上游 Channel 排空后在 Writer 所属 Mailbox 执行非终态 flush。 */
    public CompletableFuture<Void> flushForCheckpoint(long checkpointId) {
        if (checkpointId <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("checkpointId 必须为正数"));
        }
        return submitMailbox(() -> {
            writer.flush(false);
            return null;
        });
    }

    @Override
    protected void finishTask() throws Exception {
        writer.flush(true);
    }

    @Override
    protected void closeTask() throws Exception {
        if (writer != null) {
            writer.close();
        }
    }
}
