package io.yak.ops.flow.api.source;

import io.yak.ops.flow.api.checkpoint.CheckpointState;
import io.yak.ops.flow.api.row.YakRow;
import java.util.List;

/**
 * 读取一个 SourceSplit 并产出 YakRow；空批次不代表无界 Source 已结束。
 *
 * @param <SplitT> Source 分片类型
 * @author weifuwan
 * @since 2026-09-27
 */
public interface SourceReader<SplitT extends SourceSplit> extends AutoCloseable {

    /**
     * 打开需要处理的分片。
     *
     * @param split 当前分片
     * @throws Exception 打开失败
     */
    void open(SplitT split) throws Exception;

    /**
     * 读取下一批数据。返回空列表表示当前没有可用数据，是否真正结束由 isFinished 判断。持续无界 Reader 必须周期性返回，不能永久阻塞，确保运行时可以处理取消与检查点请求。
     *
     * @return 下一批 YakRow
     * @throws Exception 读取失败
     */
    List<YakRow> poll() throws Exception;

    /**
     * 判断当前分片是否已经完成。
     *
     * @return 已完成返回 true
     */
    boolean isFinished();

    /**
     * 生成 Reader 当前读取位置的检查点状态。
     *
     * @param checkpointId 检查点标识
     * @return Reader 状态
     * @throws Exception 状态生成失败
     */
    CheckpointState snapshotState(long checkpointId) throws Exception;

    /**
     * 在继续读取前恢复 Reader 状态；首次执行不会调用。
     *
     * @param state 已恢复状态
     * @throws Exception 恢复失败
     */
    void restore(CheckpointState state) throws Exception;

    /**
     * Sink 已完成对应 checkpoint barrier 的 flush 后由 Runtime 回调。
     *
     * <p>Source 可以在这里提交外部 offset 或确认上游批次；默认实现无操作。</p>
     *
     * @param checkpointId 已完成的检查点标识
     * @throws Exception 完成确认失败
     */
    default void notifyCheckpointComplete(long checkpointId) throws Exception {}

    @Override
    void close() throws Exception;
}
