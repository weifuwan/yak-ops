package io.yak.ops.flow.runtime.configuration;

import io.yak.ops.core.configuration.ConfigOption;
import io.yak.ops.core.configuration.ConfigOptions;

/** 单 JVM Runtime 的执行实现参数。 */
public final class RuntimeOptions {

    /**
     * 每个下游 Subtask 的有界队列容量。沿用旧配置键，已保存配置的行为不变。
     */
    public static final ConfigOption<Integer> CHANNEL_CAPACITY =
            ConfigOptions.key("execution.local-channel.capacity").intType().defaultValue(64);

    private RuntimeOptions() {}
}
