package org.apache.flink.cdc.composer.flink.translator;

import javax.annotation.Nullable;

/**
 * A generator for pipeline operator UIDs. Generates UIDs using a configured prefix and an
 * operator-specific suffix.
 *
 * <p>If the prefix is null, it returns null regardless of the provided suffix.
 */
public class OperatorUidGenerator {

    private final @Nullable String prefix;

    public OperatorUidGenerator(@Nullable String prefix) {
        if (prefix != null && prefix.isEmpty()) {
            throw new IllegalArgumentException(
                    "The prefix can be null but cannot be an empty string.");
        }
        this.prefix = prefix;
    }

    public OperatorUidGenerator() {
        this(null);
    }

    public @Nullable String generateUid(String suffix) {
        if (prefix == null) {
            return null;
        }
        return String.format("%s-%s", prefix, suffix);
    }
}
