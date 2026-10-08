
package io.yak.ops.core.configuration;

/** 修改配置项的操作接口。 */
public interface WritableConfig {

    <T> WritableConfig set(ConfigOption<T> option, T value);

    <T> boolean removeConfig(ConfigOption<T> option);
}
