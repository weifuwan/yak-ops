package io.yak.ops.core.api.connector.source;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 在一个 Source 并行子任务中读取已分配 Split 的运行实例。
 *
 * <p>pollNext() 必须非阻塞，不能在 Runtime 调用线程执行长时间 JDBC/CDC I/O。
 * 若暂无数据，应返回 NOTHING_AVAILABLE，并用 isAvailable() 在后续数据可读时唤醒 Runtime。
 * Reader 的所有生命周期、输入交付和读取方法由 Runtime 串行调用。
 *
 * <p>Reader 的检查点状态为尚未完成的 Split 快照，包含必要的读取进度。
 * 空列表仅代表没有未完成 Split，不自动说明以后不会再分配新 Split。
 *
 * @param <T> 输出记录类型
 * @param <SplitT> 分片类型
 * @author weifuwan
 */
public interface SourceReader<T, SplitT extends SourceSplit> extends AutoCloseable {

    /** 开始 Reader 的运行准备；恢复时可能先通过 addSplits() 注入已保存的分片。 */
    void start() throws Exception;

    /**
     * 非阻塞地产生下一批可用数据；可输出多条，建议每次只输出有界数量以保证公平性。
     *
     * <p>MORE_AVAILABLE 表示可以继续轮询；NOTHING_AVAILABLE 表示应等待可用信号；
     * END_OF_INPUT 表示所有分片均读取完毕且明确不会再收到分片。
     */
    InputStatus pollNext(ReaderOutput<T> output) throws Exception;

    /**
     * 返回下一次有数据可处理时会完成的 Future。
     *
     * <p>不可在没有数据时始终返回已完成 Future，否则 Runtime 会忙等。
     * 收到新 Split 或终止信号时，等待中的 Future 必须能被唤醒。
     */
    CompletableFuture<Void> isAvailable();

    /** 接收 Enumerator 分配或恢复后重新交付的一批 Split。 */
    void addSplits(List<SplitT> splits) throws Exception;

    /** Enumerator 不会再向这个 Reader 分配新 Split；当前 Split 可能仍在读取。 */
    void notifyNoMoreSplits();

    /**
     * 返回包含当前读取进度的未完成 Split 快照；结果须与后续读取状态隔离。
     * Runtime 负责在检查点中保存序列化后的快照。
     */
    List<SplitT> snapshotState(long checkpointId) throws Exception;

    /** 对应 Checkpoint 成功完成后通知 Reader；用于提交可安全确认的外部偏移量。 */
    default void notifyCheckpointComplete(long checkpointId) throws Exception {}

    /** 正常完成、失败或取消时释放全部 Reader 资源，包括后台读取线程。 */
    @Override
    void close() throws Exception;
}
