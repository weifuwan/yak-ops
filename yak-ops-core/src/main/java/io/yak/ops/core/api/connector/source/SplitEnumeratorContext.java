package io.yak.ops.core.api.connector.source;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

/**
 * Runtime 向 SplitEnumerator 暴露的分片协调接口。
 *
 * <p>Reader 的注册、分片投递、故障重分配和 Checkpoint 交接由 Runtime 统一管理。
 * Connector 只根据数据源特性决定应当分配哪些 Split、分配给哪个 Reader。
 *
 * <p>除 callAsync() 的后台任务外，Enumerator 回调和本 Context 的状态操作
 * 应在同一个协调器线程中串行执行；异步任务的结果必须切回协调器线程处理。
 *
 * @param <SplitT> 分片类型
 * @author weifuwan
 */
public interface SplitEnumeratorContext<SplitT extends SourceSplit> {

    /** 返回当前 Source Reader 的并行度。 */
    int currentParallelism();

    /** 返回当前已注册 Reader 的子任务 ID 的不可变快照。 */
    Set<Integer> registeredReaders();

    /**
     * 将一份 Split 交给指定 Reader。
     *
     * <p>Runtime 必须跟踪未完成交付与已确认交付，保证故障恢复后
     * 分片不会在 Enumerator 和 Reader 的状态之间丢失。
     */
    void assignSplit(SplitT split, int subtaskId);

    /**
     * 通知指定 Reader 以后不会收到新的 Split。
     *
     * <p>这不代表该 Reader 当前持有的 Split 已处理结束。
     */
    void signalNoMoreSplits(int subtaskId);

    /** Forward a connector-defined event to the active Reader attempt. */
    default void sendEventToSourceReader(int subtaskId, SourceEvent event) {
        throw new UnsupportedOperationException("This context does not support SourceEvent transport");
    }

    /**
     * 在后台执行潜在阻塞的发现操作，回调在协调器线程中执行。
     *
     * <p>后台任务不得修改 Enumerator 的共享状态；失败会传给 handler。
     */
    <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler);

    /** 从外部事件切回协调器线程，任务不得长时间阻塞。 */
    void runInCoordinatorThread(Runnable action);
}
