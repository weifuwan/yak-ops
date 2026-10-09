package io.yak.ops.core.configuration;

import java.util.Map;
import java.util.Optional;

/** Read-only view of typed configuration values. */
public interface ReadableConfig {

    /**
 * Returns an explicitly configured value or the option's default.
 *
 * @return the resolved value, which may be null when the option has no default
 */
    <T> T get(ConfigOption<T> option);

    /**
 * Returns only the explicitly stored value, ignoring its default.
 *
 * @return the stored value if the option is present
 */
    <T> Optional<T> getOptional(ConfigOption<T> option);

    /**
 * Exports explicit values as strings without masking secrets.
 *
 * <p>Do not log this map without applying appropriate credential redaction.
 *
 * @return a detached map of the stored values
 */
    Map<String, String> toMap();
}
