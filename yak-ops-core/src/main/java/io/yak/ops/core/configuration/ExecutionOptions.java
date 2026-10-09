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
     * Maximum whole-job restarts within one submission. Disabled by default.
     *
     * <p>Retries are only permitted from a valid, durably completed Source → Sink checkpoint.
     * Failed task attempts are never reused; no partial Reader-only restart is claimed.
     */
    public static final ConfigOption<Integer> MAX_RESTART_ATTEMPTS =
            ConfigOptions.key("execution.restart.max-attempts")
                    .intType()
                    .defaultValue(0);

    /** @deprecated 请使用 {@link CoreOptions#DEFAULT_PARALLELISM}；此字段仅作源码兼容别名。 */
    @Deprecated
    public static final ConfigOption<Integer> DEFAULT_PARALLELISM = CoreOptions.DEFAULT_PARALLELISM;

    /** @deprecated 请使用 {@link CheckpointingOptions#CHECKPOINTING_INTERVAL}；此字段仅作源码兼容别名。 */
    @Deprecated
    public static final ConfigOption<Duration> CHECKPOINT_INTERVAL = CheckpointingOptions.CHECKPOINTING_INTERVAL;

    private ExecutionOptions() {}
}
