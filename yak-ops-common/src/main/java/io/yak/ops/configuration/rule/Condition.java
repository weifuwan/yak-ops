package io.yak.ops.configuration.rule;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.ReadonlyConfig;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A typed configuration predicate, including composite and cross-field conditions.
 *
 * @param <T> type of the primary option
 */
public final class Condition<T> {

    private final Option<T> option;
    private final ConditionOperator operator;
    private final Object expected;
    private final Option<?> compareOption;
    private final ConditionExtension<T> extension;
    private final Condition<?> left;
    private final Condition<?> right;

    Condition(
            Option<T> option,
            ConditionOperator operator,
            Object expected,
            Option<?> compareOption,
            ConditionExtension<T> extension) {
        this(option, operator, expected, compareOption, extension, null, null);
    }

    private Condition(
            Option<T> option,
            ConditionOperator operator,
            Object expected,
            Option<?> compareOption,
            ConditionExtension<T> extension,
            Condition<?> left,
            Condition<?> right) {
        this.option = option;
        this.operator = Objects.requireNonNull(operator, "operator");
        this.expected = expected;
        this.compareOption = compareOption;
        this.extension = extension;
        this.left = left;
        this.right = right;
    }

    public static <T> Condition<T> of(Option<T> option, T expected) {
        return new Condition<>(Objects.requireNonNull(option, "option"), ConditionOperator.EQUAL, expected, null, null);
    }

    /** Combines predicates using logical AND. */
    public Condition<T> and(Condition<?> condition) {
        return new Condition<>(
                null, ConditionOperator.AND, null, null, null, this, Objects.requireNonNull(condition, "condition"));
    }

    /** Combines predicates using logical OR. */
    public Condition<T> or(Condition<?> condition) {
        return new Condition<>(
                null, ConditionOperator.OR, null, null, null, this, Objects.requireNonNull(condition, "condition"));
    }

    public boolean evaluate(ReadonlyConfig config) {
        return ConditionEvaluators.evaluate(this, config);
    }

    public Option<T> option() {
        return option;
    }

    public ConditionOperator operator() {
        return operator;
    }

    public Object expected() {
        return expected;
    }

    public Option<?> compareOption() {
        return compareOption;
    }

    public ConditionExtension<T> extension() {
        return extension;
    }

    public Condition<?> left() {
        return left;
    }

    public Condition<?> right() {
        return right;
    }

    /** All option names this condition reads. */
    public Set<String> optionKeys() {
        Set<String> keys = new LinkedHashSet<>();
        if (option != null) {
            keys.add(option.key());
        }
        if (compareOption != null) {
            keys.add(compareOption.key());
        }
        if (left != null) {
            keys.addAll(left.optionKeys());
        }
        if (right != null) {
            keys.addAll(right.optionKeys());
        }
        return keys;
    }

    /** Returns a stable description containing field names rather than actual configured values. */
    public String description() {
        if (operator == ConditionOperator.AND || operator == ConditionOperator.OR) {
            return "(" + left.description() + " " + operator + " " + right.description() + ")";
        }
        if (operator == ConditionOperator.EXTENSION) {
            return extension.description();
        }
        return option.key() + " " + operator + (compareOption == null ? "" : " " + compareOption.key());
    }
}
