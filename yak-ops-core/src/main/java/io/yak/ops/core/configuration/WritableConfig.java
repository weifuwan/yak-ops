package io.yak.ops.core.configuration;

/** Operations for changing explicit typed configuration entries. */
public interface WritableConfig {

    /** Sets an explicit value for the typed option. */
    <T> WritableConfig set(ConfigOption<T> option, T value);

    /** Removes an explicitly stored option without changing its default. */
    <T> boolean removeConfig(ConfigOption<T> option);
}
