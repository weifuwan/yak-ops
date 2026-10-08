package io.yak.ops.flow.runtime.operators;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEventHandler;
import io.yak.ops.flow.runtime.source.event.AddSplitEvent;
import io.yak.ops.flow.runtime.source.event.NoMoreSplitsEvent;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * SourceReader 的运行时包装层，对齐 Flink SourceOperator 的职责。
 *
 * <p>只在所属 StreamTask 的 Mailbox 线程中创建、启动、读取和关闭 Reader；
 * 不创建工作线程，也不负责 SplitEnumerator 的生命周期。
 * SourceReaderContext 由 Runtime 单独提供；下游 ReaderOutput 的背压由上层任务执行线程承接。
 *
 * @param <T> 数据记录类型
 * @param <SplitT> 分片类型
 */
public final class SourceOperator<T, SplitT extends SourceSplit>
        implements OperatorEventHandler, AutoCloseable {

    private final Source<T, SplitT, ?> source;
    private final SourceReaderContext readerContext;
    private SourceReader<T, SplitT> reader;
    private boolean started;
    private boolean noMoreSplits;
    private boolean finished;

    public SourceOperator(Source<T, SplitT, ?> source, SourceReaderContext readerContext) {
        this.source = Objects.requireNonNull(source, "source 不能为空");
        this.readerContext = Objects.requireNonNull(readerContext, "readerContext 不能为空");
    }

    /** 创建 Reader，必须在所属 Task 线程执行，并在注册 Coordinator 之前完成。 */
    public void initialize() throws Exception {
        if (reader != null) {
            throw new IllegalStateException("SourceOperator 已初始化");
        }
        reader = Objects.requireNonNull(source.createReader(readerContext), "Source 返回空 Reader");
    }

    /** 在 Reader.start 之前注入从已完成 Checkpoint 反序列化的 Split 进度。 */
    public void restoreSplits(List<SplitT> splits) throws Exception {
        ensureInitialized();
        if (started) {
            throw new IllegalStateException("已经启动的 Reader 不允许直接恢复 Split");
        }
        if (!splits.isEmpty()) {
            reader.addSplits(List.copyOf(splits));
        }
    }

    /** Reader 注册完成后启动读取组件。 */
    public void start() throws Exception {
        ensureInitialized();
        if (started) {
            throw new IllegalStateException("SourceReader 已启动");
        }
        started = true;
        reader.start();
    }

    /**
     * 统一处理 Coordinator 发送的控制事件；只支持分片投递与分片结束两种事件。
     *
     * <p>这是当前同 JVM 的类型化 SourceCoordinator 与 SourceOperator 之间的专用连接；
     * 泛型擦除后的拆包转换被限制在本类，不允许外部任意构造异构 Split。
     */
    @Override
    public void handleOperatorEvent(OperatorEvent event) throws Exception {
        Objects.requireNonNull(event, "event 不能为空");
        if (event instanceof AddSplitEvent<?> splitsEvent) {
            handleAddSplits(splitsEvent);
        } else if (event instanceof NoMoreSplitsEvent noMoreEvent) {
            handleNoMoreSplits(noMoreEvent);
        } else {
            throw new IllegalArgumentException("SourceOperator 不支持的 OperatorEvent："
                    + event.getClass().getName());
        }
    }

    /** SourceCoordinator 发送的 Split 事件，由 StreamTask 在 Mailbox 线程串行处理。 */
    @SuppressWarnings("unchecked")
    private void handleAddSplits(AddSplitEvent<?> event) throws Exception {
        ensureInitialized();
        Objects.requireNonNull(event, "event 不能为空");
        if (noMoreSplits || finished) {
            throw new IllegalStateException("NoMoreSplits 后不能再交付新 Split");
        }
        // 类型由 SourceCoordinator<SplitT, ?> 与本 SourceOperator 的连接保证。
        reader.addSplits((List<SplitT>) event.splits());
    }

    /** 只通知未来不再分配 Split，不能立即视为输入结束。 */
    private void handleNoMoreSplits(NoMoreSplitsEvent event) {
        ensureInitialized();
        Objects.requireNonNull(event, "event 不能为空");
        if (noMoreSplits) {
            throw new IllegalStateException("NoMoreSplits 重复交付");
        }
        reader.notifyNoMoreSplits();
        noMoreSplits = true;
    }

    /** 在 Task 线程非阻塞读取下一批记录并输出到下游。 */
    public InputStatus emitNext(ReaderOutput<T> output) throws Exception {
        ensureStarted();
        if (finished) {
            return InputStatus.END_OF_INPUT;
        }
        InputStatus status = Objects.requireNonNull(reader.pollNext(output), "pollNext 不能返回 null");
        if (status == InputStatus.END_OF_INPUT) {
            if (!noMoreSplits) {
                throw new IllegalStateException("Reader 未收到 NoMoreSplits 却报告 END_OF_INPUT");
            }
            finished = true;
        }
        return status;
    }

    /** 暂无数据时提供可用性信号，由 StreamTask 决定等待方式。 */
    public CompletableFuture<Void> isAvailable() {
        ensureStarted();
        return Objects.requireNonNull(reader.isAvailable(), "isAvailable 不能返回 null");
    }

    /** 获取本 Reader 的分片进度快照；不代表完整的 Job Checkpoint。 */
    public List<SplitT> snapshotState(long checkpointId) throws Exception {
        ensureStarted();
        if (checkpointId < 0) {
            throw new IllegalArgumentException("checkpointId 不能为负数");
        }
        return List.copyOf(Objects.requireNonNull(reader.snapshotState(checkpointId), "Reader 快照不能为空"));
    }

    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        ensureStarted();
        reader.notifyCheckpointComplete(checkpointId);
    }

    @Override
    public void close() throws Exception {
        if (reader != null) {
            try {
                reader.close();
            } finally {
                reader = null;
            }
        }
    }

    private void ensureInitialized() {
        if (reader == null) {
            throw new IllegalStateException("SourceReader 尚未初始化");
        }
    }

    private void ensureStarted() {
        ensureInitialized();
        if (!started) {
            throw new IllegalStateException("SourceReader 尚未启动");
        }
    }
}
