package io.yak.ops.configuration.rule;

import java.util.Objects;

/** Nested rules activated only when the conditional expression matches. */
public record ConditionRule(Expression when, OptionRule optionRule) {

    public ConditionRule {
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(optionRule, "optionRule");
    }
}
