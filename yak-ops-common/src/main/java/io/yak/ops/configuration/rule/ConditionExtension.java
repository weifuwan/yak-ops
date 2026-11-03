package io.yak.ops.configuration.rule;

import io.yak.ops.configuration.ReadonlyConfig;

/**
 * Extension point for domain-specific value constraints.
 *
 * @param <T> type of the option being checked
 */
public interface ConditionExtension<T> {

    /** Non-sensitive description of the constraint. */
    String description();

    /** Returns true when the configured value satisfies this constraint. */
    boolean evaluate(ReadonlyConfig config, T value);
}
