package io.yak.ops.flow.runtime.tasks;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.io.StreamTaskSourceInput;
import io.yak.ops.flow.runtime.operators.SourceOperator;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;
import io.yak.ops.flow.runtime.operators.coordination.SubtaskGateway;
import io.yak.ops.flow.runtime.source.coordinator.SourceCoordinator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 使用 StreamTask Mailbox 运行一个 SourceOperator。
 *
 * <p>接收来自 Coordinator 的 Split 事件，事件确认发生在 SourceReader 实际处理之后。
 * 不自行创建第二条 Reader 线程；SourceReader 全部生命周期由本 Task 的线程串行执行。
 *
 * <p>下游输出与 Checkpoint Barrier 的全局协调不属于该类。
 */
public final class SourceOperatorStreamTask<T, SplitT extends SourceSplit>
        extends StreamTask implements SubtaskGateway {

    private final SourceCoordinator<T, SplitT, ?> coordinator;
    private final SourceOperator<T, SplitT> operator;
    private final StreamTaskSourceInput<T> input;

    public SourceOperatorStreamTask(
            Source<T, SplitT, ?> source,
            SourceCoordinator<T, SplitT, ?> coordinator,
            TaskEnvironment environment,
            ReaderOutput<T> output) {
        super(environment);
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator 不能为空");
        this.operator = new SourceOperator<>(source, taskEnvironment(),
                event -> coordinator.handleEventFromOperator(taskInfo().subtaskIndex(), event), this::failAsync);
        this.input = new StreamTaskSourceInput<>(operator, output);
    }

    @Override
    protected void openTask() throws Exception {
        operator.initialize();
        // 必须先注册 Gateway，再启动可能调用 sendSplitRequest() 的 Reader。
        coordinator.registerReader(taskInfo().subtaskIndex(), this).get();
        operator.start();
    }

    @Override
    protected InputStatus processInput() throws Exception {
        return input.emitNext();
    }

    @Override
    protected CompletableFuture<Void> getAvailableFuture() {
        return input.getAvailableFuture();
    }

    @Override
    protected void closeTask() throws Exception {
        operator.close();
    }

    @Override
    protected void taskFailed(Throwable failure) {
        coordinator.readerFailed(taskInfo().subtaskIndex(), failure);
    }

    /**
     * Coordinator 的统一事件入口。事件处理与 pollNext() 由 StreamTask Mailbox 串行执行。
     *
     * <p>Future 在 SourceOperator 真正处理事件后完成。由于当前实现仅限同 JVM，
     * 该确认强于一般网络送达确认，但不等于数据消费完成或 Checkpoint 成功。
     */
    @Override
    public CompletionStage<Void> sendEvent(OperatorEvent event) {
        Objects.requireNonNull(event, "event 不能为空");
        return submitMailbox(() -> {
            operator.handleOperatorEvent(event);
            return null;
        });
    }

    /** Reader 运行线程上的进度快照；不代表全局 Checkpoint 完成。 */
    public CompletableFuture<List<SplitT>> snapshotState(long checkpointId) {
        return submitMailbox(() -> operator.snapshotState(checkpointId));
    }

    /** 完整 Job Checkpoint 完成后才由上层调度调用。 */
    public CompletableFuture<Void> notifyCheckpointComplete(long checkpointId) {
        return submitMailbox(() -> {
            operator.notifyCheckpointComplete(checkpointId);
            return null;
        });
    }
}
