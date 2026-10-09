package io.yak.ops.flow.runtime.configuration;

import io.yak.ops.core.configuration.ConfigOption;
import io.yak.ops.core.configuration.ConfigOptions;

/** Configuration options for the single-JVM runtime implementation. */
public final class RuntimeOptions {

    /**
     * Bounded record-buffer capacity for each downstream subtask.
     *
     * <p>Retains its established configuration key so persisted configuration behaves consistently.
     */
    public static final ConfigOption<Integer> CHANNEL_CAPACITY =
            ConfigOptions.key("execution.local-channel.capacity").intType().defaultValue(64);

    private RuntimeOptions() {}
}
