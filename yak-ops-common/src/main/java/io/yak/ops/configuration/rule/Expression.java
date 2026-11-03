package io.yak.ops.configuration.rule;

import io.yak.ops.configuration.ReadonlyConfig;
import java.util.Objects;

/** Composable conditional expression used by OptionRule. */
public final class Expression {

    private final Condition<?> condition;

    private Expression(Condition<?> condition) {
        this.condition = Objects.requireNonNull(condition, "condition");
    }

    public static Expression of(Condition<?> condition) {
        return new Expression(condition);
    }

    public Expression and(Expression other) {
        return new Expression(condition.and(other.condition));
    }

    public Expression or(Expression other) {
        return new Expression(condition.or(other.condition));
    }

    public boolean evaluate(ReadonlyConfig config) {
        return condition.evaluate(config);
    }

    public Condition<?> condition() {
        return condition;
    }
}
