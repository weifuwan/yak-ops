
package io.yak.ops.core.configuration;

import java.util.Map;
import java.util.Optional;

/** Read-only operations on a configuration container. */
public interface ReadableConfig {

    /** Returns the stored value, or the option's default when absent (possibly null). */
    <T> T get(ConfigOption<T> option);

    /** Returns only an explicitly stored value; the option default is not considered. */
    <T> Optional<T> getOptional(ConfigOption<T> option);

    /** Exports explicitly stored settings; values are not redacted. */
    Map<String, String> toMap();
}
