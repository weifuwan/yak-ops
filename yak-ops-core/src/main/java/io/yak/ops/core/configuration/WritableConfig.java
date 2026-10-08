
package io.yak.ops.core.configuration;

/** Operations for changing configuration entries. */
public interface WritableConfig {

    <T> WritableConfig set(ConfigOption<T> option, T value);

    <T> boolean removeConfig(ConfigOption<T> option);
}
