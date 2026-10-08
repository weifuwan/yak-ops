package io.yak.ops.core.configuration;

/** 通用执行配置；与单次 Job 的运行状态无关。 */
public final class CoreOptions {

    /** 未显式设置并行度的算子采用的默认值；构图时解析，必须为正整数。 */
    public static final ConfigOption<Integer> DEFAULT_PARALLELISM =
            ConfigOptions.key("parallelism.default").intType().defaultValue(1);

    /** 每个下游子任务的本地 Channel 最大排队记录数；超过容量时上游阻塞等待。 */
    public static final ConfigOption<Integer> LOCAL_CHANNEL_CAPACITY =
            ConfigOptions.key("execution.local-channel.capacity").intType().defaultValue(64);

    private CoreOptions() {}
}
