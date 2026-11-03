package io.yak.ops.configuration.rule;

import io.yak.ops.configuration.ReadonlyConfig;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Pure evaluation of a declared Condition against one immutable configuration. */
public final class ConditionEvaluators {

    private ConditionEvaluators() {}

    public static boolean evaluate(Condition<?> condition, ReadonlyConfig config) {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(config, "config");
        if (condition.operator() == ConditionOperator.AND) {
            return evaluate(condition.left(), config) && evaluate(condition.right(), config);
        }
        if (condition.operator() == ConditionOperator.OR) {
            return evaluate(condition.left(), config) || evaluate(condition.right(), config);
        }
        Object value = config.get(condition.option());
        Object expected =
                condition.compareOption() == null ? condition.expected() : config.get(condition.compareOption());
        return switch (condition.operator()) {
            case EQUAL -> Objects.equals(value, expected);
            case NOT_EQUAL -> !Objects.equals(value, expected);
            case GREATER_THAN -> value != null && expected != null && compare(value, expected) > 0;
            case GREATER_OR_EQUAL -> value != null && expected != null && compare(value, expected) >= 0;
            case LESS_THAN -> value != null && expected != null && compare(value, expected) < 0;
            case LESS_OR_EQUAL -> value != null && expected != null && compare(value, expected) <= 0;
            case NOT_BLANK -> value instanceof String text && !text.isBlank();
            case STARTS_WITH -> value instanceof String text && text.startsWith((String) expected);
            case CONTAINS -> value instanceof String text && text.contains((String) expected);
            case MATCHES -> value instanceof String text && text.matches((String) expected);
            case NOT_EMPTY -> value instanceof Collection<?> collection && !collection.isEmpty();
            case UNIQUE ->
                value instanceof Collection<?> collection && new HashSet<>(collection).size() == collection.size();
            case MAP_NOT_EMPTY -> value instanceof Map<?, ?> map && !map.isEmpty();
            case MAP_CONTAINS_KEY -> value instanceof Map<?, ?> map && map.containsKey(expected);
            case MAP_CONTAINS_KEYS ->
                value instanceof Map<?, ?> map
                        && expected instanceof Collection<?> keys
                        && map.keySet().containsAll(keys);
            case EXTENSION -> evaluateExtension(condition, config, value);
            case AND, OR -> throw new IllegalStateException("Composite condition handled above");
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> boolean evaluateExtension(Condition<T> condition, ReadonlyConfig config, Object value) {
        return condition.extension().evaluate(config, (T) value);
    }

    private static int compare(Object value, Object expected) {
        if (value instanceof Number left && expected instanceof Number right) {
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()));
        }
        if (value instanceof Comparable<?> comparable && value.getClass().isInstance(expected)) {
            @SuppressWarnings("unchecked")
            Comparable<Object> typed = (Comparable<Object>) comparable;
            return typed.compareTo(expected);
        }
        throw new IllegalArgumentException("Constraint values must have comparable types");
    }
}
