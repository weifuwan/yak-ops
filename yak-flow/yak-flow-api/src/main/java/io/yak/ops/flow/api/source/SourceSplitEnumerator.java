package io.yak.ops.flow.api.source;

import io.yak.ops.flow.api.checkpoint.CheckpointState;
import java.util.Optional;

/**
 * 负责产生 SourceSplit 并保存分片分配进度；既可枚举有限 JDBC 分片，也可为 CDC Source 提供持续读取分片。
 *
 * @param <SplitT> Source 分片类型
 * @author weifuwan
 * @since 2026-09-27
 */
public interface SourceSplitEnumerator<SplitT extends SourceSplit> extends AutoCloseable {

    /**
     * 初始化枚举器拥有的资源。
     *
     * @throws Exception 初始化失败
     */
    default void start() throws Exception {}

    /**
     * 返回下一份可分配分片；暂时或永久无分片时返回空，调用方结合 isFinished 判断生命周期。
     *
     * @return 下一份分片
     * @throws Exception 枚举失败
     */
    Optional<SplitT> nextSplit() throws Exception;

    /**
     * 判断枚举器是否已经不会再产生新的分片。
     *
     * @return 已完成返回 true
     */
    boolean isFinished();

    /**
     * 生成当前枚举进度的检查点状态。
     *
     * @param checkpointId 检查点标识
     * @return 枚举器状态
     * @throws Exception 状态生成失败
     */
    CheckpointState snapshotState(long checkpointId) throws Exception;

    /**
     * 从已持久化状态恢复枚举进度；首次执行不会调用。
     *
     * @param state 已恢复状态
     * @throws Exception 恢复失败
     */
    void restore(CheckpointState state) throws Exception;

    @Override
    default void close() throws Exception {}
}
