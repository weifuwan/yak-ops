package io.yak.ops.core.configuration;

import io.yak.ops.core.api.RuntimeExecutionMode;
import java.time.Duration;

/**
 * 控制已提交 Pipeline 运行方式的类型化配置项。
 *
 * <p>本类只声明配置项；运行准备阶段负责校验取值范围，
 * 并检查配置是否与 Pipeline 兼容。
 */
public final class ExecutionOptions {

    /**
     * 运行模式：所有 Source 均有界时，AUTOMATIC 选择 BATCH；
     * 否则选择 STREAMING。显式使用 BATCH 时要求所有 Source 均有界。
     */
    public static final ConfigOption<RuntimeExecutionMode> RUNTIME_MODE =
            ConfigOptions.key("execution.runtime-mode")
                    .enumType(RuntimeExecutionMode.class)
                    .defaultValue(RuntimeExecutionMode.AUTOMATIC);

    /**
     * 未显式指定并行度的算子使用的默认并行度。
     * 最终解析的并行度必须大于 0。
     */
    public static final ConfigOption<Integer> DEFAULT_PARALLELISM =
            ConfigOptions.key("parallelism.default")
                    .intType()
                    .defaultValue(1);

    /**
     * 周期性 Checkpoint 的触发间隔；Duration.ZERO 表示禁用。
     * 负数间隔无效。仅设置此间隔不代表具备持久化恢复能力，
     * 也不代表具备端到端 Exactly-once 语义。
     */
    public static final ConfigOption<Duration> CHECKPOINT_INTERVAL =
            ConfigOptions.key("execution.checkpointing.interval")
                    .durationType()
                    .defaultValue(Duration.ZERO);

    private ExecutionOptions() {}
}
