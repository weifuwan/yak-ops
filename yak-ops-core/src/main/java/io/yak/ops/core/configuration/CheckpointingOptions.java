
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

    private CheckpointingOptions() {}
}
