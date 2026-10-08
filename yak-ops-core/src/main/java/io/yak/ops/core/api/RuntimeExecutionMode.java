package io.yak.ops.core.api;

/**
 * 有界与无界 Pipeline 的运行模式。
 *
 * <p>在 Pipeline 启动前确定运行模式。若 Pipeline 包含无界 Source，
 * 执行器必须拒绝 {@link #BATCH} 模式。
 */
public enum RuntimeExecutionMode {
    /** 根据所有 Source 的有界性自动确定运行模式。 */
    AUTOMATIC,

    /** 使用批处理语义执行有界 Pipeline。 */
    BATCH,

    /** 使用流处理语义执行有界或无界 Pipeline。 */
    STREAMING
}
