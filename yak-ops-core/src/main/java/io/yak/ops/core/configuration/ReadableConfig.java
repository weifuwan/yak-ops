
package io.yak.ops.core.configuration;

import java.util.Map;
import java.util.Optional;

/** 配置容器的只读操作接口。 */
public interface ReadableConfig {

    /** 返回存储的配置值；未设置时返回配置项默认值（可能为 null）。 */
    <T> T get(ConfigOption<T> option);

    /** 仅返回显式设置的值，不考虑配置项的默认值。 */
    <T> Optional<T> getOptional(ConfigOption<T> option);

    /** 导出显式设置的配置项，导出的值不会脱敏。 */
    Map<String, String> toMap();
}
