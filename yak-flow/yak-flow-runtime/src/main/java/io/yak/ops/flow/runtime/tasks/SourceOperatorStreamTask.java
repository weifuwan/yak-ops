package io.yak.ops.flow.runtime.tasks;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.io.RecordRouter;
import io.yak.ops.flow.runtime.io.StreamTaskSourceInput;
import io.yak.ops.flow.runtime.operators.OperatorChain;
import io.yak.ops.flow.runtime.operators.SourceOperator;
import io.yak.ops.flow.runtime.operators.SourceReaderRuntimeContext;
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
 * <p>下游输出由 ReaderOutput / RecordRouter 或内联 OperatorChain 承接；
 * 全局 Checkpoint 不属于该类。
 */
public final class SourceOperatorStreamTask<T, SplitT extends SourceSplit>
        extends StreamTask implements SubtaskGateway {

    private final SourceCoordinator<SplitT, ?> coordinator;
    private final SourceOperator<T, SplitT> operator;
    private final StreamTaskSourceInput<T> input;
    private final OperatorChain operatorChain;
    private final RecordRouter<?> resultPartition;
    private final List<SplitT> restoredSplits;
    private boolean pausedForCheckpoint;
    private CompletableFuture<Void> resumeFuture = CompletableFuture.completedFuture(null);

    public SourceOperatorStreamTask(
            Source<T, SplitT, ?> source,
            SourceCoordinator<SplitT, ?> coordinator,
            TaskEnvironment environment,
            ReaderOutput<T> output) {
        this(source, coordinator, environment, output, null, List.of());
    }

    /** 为内联 Operator Chain 建立完整的 Task 生命周期；不创建额外任务线程。 */
    public SourceOperatorStreamTask(
            Source<T, SplitT, ?> source,
            SourceCoordinator<SplitT, ?> coordinator,
            TaskEnvironment environment,
            ReaderOutput<T> output,
            OperatorChain operatorChain) {
        this(source, coordinator, environment, output, operatorChain, List.of());
    }

    /** 从已完成 Checkpoint 注入未完成 Split，仍在 Task Mailbox 线程初始化 Reader。 */
    public SourceOperatorStreamTask(
            Source<T, SplitT, ?> source,
            SourceCoordinator<SplitT, ?> coordinator,
            TaskEnvironment environment,
            ReaderOutput<T> output,
            OperatorChain operatorChain,
            List<SplitT> restoredSplits) {
        super(environment);
        this.restoredSplits = List.copyOf(Objects.requireNonNull(restoredSplits, "restoredSplits 不能为空"));
        this.operatorChain = operatorChain;
        this.resultPartition = output instanceof RecordRouter<?> partition ? partition : null;
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator 不能为空");
        this.coordinator.coordinatorContext().validateTask(taskInfo());
        SourceReaderRuntimeContext readerContext = new SourceReaderRuntimeContext(
                taskEnvironment(), event -> coordinator.handleEventFromOperator(taskInfo(), event), this::failAsync);
        this.operator = new SourceOperator<>(source, readerContext);
        this.input = new StreamTaskSourceInput<>(operator, output);
    }

    @Override
    protected void openTask() throws Exception {
        if (operatorChain != null) {
            operatorChain.open();
        }
        operator.initialize();
        operator.restoreSplits(restoredSplits);
        // 必须先注册 Gateway，再启动可能调用 sendSplitRequest() 的 Reader。
        coordinator.registerReader(taskInfo(), this).get();
        operator.start();
    }

    @Override
    protected InputStatus processInput() throws Exception {
        return pausedForCheckpoint ? InputStatus.NOTHING_AVAILABLE : input.emitNext();
    }

    @Override
    protected CompletableFuture<Void> getAvailableFuture() {
        return pausedForCheckpoint ? resumeFuture : input.getAvailableFuture();
    }

    @Override
    protected void finishTask() throws Exception {
        if (operatorChain != null) {
            operatorChain.finish();
        }
        if (resultPartition != null) {
            resultPartition.finish();
        }
    }

    @Override
    protected void closeTask() throws Exception {
        try (OperatorChain chain = operatorChain; SourceOperator<T, SplitT> reader = operator) {
            // 逆序关闭 Reader，然后关闭 Operator Chain；异常由 try-with-resources 聚合。
        }
    }

    /** Coordinator 异步失败时唤醒 Task Mailbox，由统一 Task 生命周期执行关闭。 */
    public void coordinatorFailed(Throwable failure) {
        failAsync(failure);
    }

    @Override
    protected void taskFailed(Throwable failure) {
        coordinator.readerFailed(taskInfo(), failure);
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

    /**
     * Source 侧对齐切面：Mailbox 中先快照 Reader，再阻止下一次 pollNext()。
     * 控制事件仍会在 Mailbox 中处理，协调侧必须先冻结 Split 请求/分配。
     */
    public CompletableFuture<List<SplitT>> pauseForCheckpoint(long checkpointId) {
        return submitMailbox(() -> {
            if (pausedForCheckpoint) {
                throw new IllegalStateException("Reader 已被另一个 Checkpoint 暂停");
            }
            List<SplitT> state = operator.snapshotState(checkpointId);
            pausedForCheckpoint = true;
            resumeFuture = new CompletableFuture<>();
            return state;
        });
    }

    /** Checkpoint 成败后都要恢复 Source 轮询；只由 Runtime CheckpointCoordinator 调用。 */
    public CompletableFuture<Void> resumeAfterCheckpoint() {
        return submitMailbox(() -> {
            if (pausedForCheckpoint) {
                pausedForCheckpoint = false;
                resumeFuture.complete(null);
            }
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
