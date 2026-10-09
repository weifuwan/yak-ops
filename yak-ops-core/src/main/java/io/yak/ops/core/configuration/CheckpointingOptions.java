
package io.yak.ops.core.configuration;

import java.time.Duration;

/**
 * 周期性 Checkpoint 的配置项。
 *
 * <p>本类只声明类型化配置项。执行引擎负责校验配置值，
 * 并将其应用于 Checkpoint 协调器。
 *
 * <p>设计参考 Apache Flink 的 CheckpointingOptions。
 *
 * @author weifuwan
 */
public final class CheckpointingOptions {

    /**
     * 周期性 Checkpoint 触发的基础间隔。
     *
     * <p>未设置或 Duration.ZERO 表示禁用周期性 Checkpoint。
     * 负数间隔无效。
     */
    public static final ConfigOption<Duration> CHECKPOINTING_INTERVAL =
            ConfigOptions.key("execution.checkpointing.interval")
                    .durationType()
                    .defaultValue(Duration.ZERO);

    /**
     * 单次 Checkpoint 尝试允许的最长时间。
     * 超过该时间的 Checkpoint 应被丢弃。
     */
    public static final ConfigOption<Duration> CHECKPOINTING_TIMEOUT =
            ConfigOptions.key("execution.checkpointing.timeout")
                    .durationType()
                    .defaultValue(Duration.ofMinutes(10));

    /**
     * 两次 Checkpoint 尝试之间的最小间隔。
     *
     * <p>只允许一个 Checkpoint 并发执行时，
     * 可确保相邻尝试之间存在空闲时间。
     */
    public static final ConfigOption<Duration> MIN_PAUSE_BETWEEN_CHECKPOINTS =
            ConfigOptions.key("execution.checkpointing.min-pause")
                    .durationType()
                    .defaultValue(Duration.ZERO);

    /**
     * 同时进行的 Checkpoint 尝试数量上限。
     *
     * <p>如果引擎仅支持一个正在进行的 Checkpoint，
     * 则必须拒绝大于 1 的配置值。
     */
    public static final ConfigOption<Integer> MAX_CONCURRENT_CHECKPOINTS =
            ConfigOptions.key("execution.checkpointing.max-concurrent-checkpoints")
                    .intType()
                    .defaultValue(1);

    /**
     * 本地已完成 Checkpoint 的持久化目录。
     *
     * <p>启用周期 Checkpoint 时必须显式设置。不同 Job 不应共享目录并发写入，
     * Runtime 持有目录级独占锁并使用原子替换更新已完成状态。
     */
    public static final ConfigOption<String> STATE_DIRECTORY =
            ConfigOptions.key("execution.checkpointing.state-directory")
                    .stringType()
                    .noDefaultValue();

    /**
     * 是否从该状态目录中最近一次已完成的 Checkpoint 恢复。
     *
     * <p>恢复要求稳定的算子 UID、完全兼容的拓扑和并行度。
     */
    public static final ConfigOption<Boolean> RESTORE_LATEST =
            ConfigOptions.key("execution.checkpointing.restore-latest")
                    .booleanType()
                    .defaultValue(false);

    private CheckpointingOptions() {}
}
