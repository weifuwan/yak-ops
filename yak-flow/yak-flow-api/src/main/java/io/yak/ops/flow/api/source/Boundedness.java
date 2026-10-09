package io.yak.ops.flow.api.source;

/**
 * 描述 Source 是否会自然结束，用于统一批量与持续流式读取的生命周期语义。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public enum Boundedness {

    /** 有界 Source，全部数据读取完成后任务可以自然结束。 */
    BOUNDED,

    /** 持续无界 Source，除非取消或失败，否则不会因为暂时没有数据而结束。 */
    CONTINUOUS_UNBOUNDED
}
