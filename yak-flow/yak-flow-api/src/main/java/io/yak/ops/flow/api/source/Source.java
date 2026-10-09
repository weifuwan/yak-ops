package io.yak.ops.flow.api.source;

/**
 * YakFlow 批流统一 Source 契约；是否自然结束由 Boundedness 声明，而不是通过不同 Source 类型体系区分。
 *
 * @param <SplitT> Source 分片类型
 * @author weifuwan
 * @since 2026-09-27
 */
public interface Source<SplitT extends SourceSplit> {

    /**
     * 返回 Source 的生命周期边界。
     *
     * @return 有界或持续无界
     */
    Boundedness boundedness();

    /**
     * 为一次执行创建独立的分片枚举器。
     *
     * @return 分片枚举器
     */
    SourceSplitEnumerator<SplitT> createEnumerator();

    /**
     * 为一个 Source Task 创建独立 Reader。
     *
     * @return Source Reader
     */
    SourceReader<SplitT> createReader();
}
