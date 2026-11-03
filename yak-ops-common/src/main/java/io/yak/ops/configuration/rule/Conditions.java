package io.yak.ops.configuration.rule;

import io.yak.ops.configuration.Option;
import java.util.Arrays;
import java.util.Objects;

/** Builders for built-in option value constraints. */
public final class Conditions {

    private Conditions() {}

    public static <T> Condition<T> equal(Option<T> option, T expected) {
        return Condition.of(option, expected);
    }

    public static <T> Condition<T> notEqual(Option<T> option, T expected) {
        return new Condition<>(option, ConditionOperator.NOT_EQUAL, expected, null, null);
    }

    public static <T extends Comparable<? super T>> Condition<T> greaterThan(Option<T> option, T threshold) {
        return compare(option, ConditionOperator.GREATER_THAN, threshold);
    }

    public static <T extends Comparable<? super T>> Condition<T> greaterOrEqual(Option<T> option, T threshold) {
        return compare(option, ConditionOperator.GREATER_OR_EQUAL, threshold);
    }

    public static <T extends Comparable<? super T>> Condition<T> lessThan(Option<T> option, T threshold) {
        return compare(option, ConditionOperator.LESS_THAN, threshold);
    }

    public static <T extends Comparable<? super T>> Condition<T> lessOrEqual(Option<T> option, T threshold) {
        return compare(option, ConditionOperator.LESS_OR_EQUAL, threshold);
    }

    public static <T extends Comparable<? super T>> Condition<T> lessThanField(
            Option<T> option, Option<T> compareOption) {
        return compareFields(option, ConditionOperator.LESS_THAN, compareOption);
    }

    public static <T extends Comparable<? super T>> Condition<T> lessOrEqualField(
            Option<T> option, Option<T> compareOption) {
        return compareFields(option, ConditionOperator.LESS_OR_EQUAL, compareOption);
    }

    public static <T extends Comparable<? super T>> Condition<T> greaterThanField(
            Option<T> option, Option<T> compareOption) {
        return compareFields(option, ConditionOperator.GREATER_THAN, compareOption);
    }

    public static <T extends Comparable<? super T>> Condition<T> greaterOrEqualField(
            Option<T> option, Option<T> compareOption) {
        return compareFields(option, ConditionOperator.GREATER_OR_EQUAL, compareOption);
    }

    public static Condition<String> notBlank(Option<String> option) {
        return new Condition<>(option, ConditionOperator.NOT_BLANK, null, null, null);
    }

    public static Condition<String> startsWith(Option<String> option, String prefix) {
        return new Condition<>(option, ConditionOperator.STARTS_WITH, prefix, null, null);
    }

    public static Condition<String> contains(Option<String> option, String part) {
        return new Condition<>(option, ConditionOperator.CONTAINS, part, null, null);
    }

    public static Condition<String> matches(Option<String> option, String pattern) {
        return new Condition<>(option, ConditionOperator.MATCHES, pattern, null, null);
    }

    public static <T> Condition<T> notEmpty(Option<T> option) {
        return new Condition<>(option, ConditionOperator.NOT_EMPTY, null, null, null);
    }

    public static <T> Condition<T> unique(Option<T> option) {
        return new Condition<>(option, ConditionOperator.UNIQUE, null, null, null);
    }

    public static <T> Condition<T> mapNotEmpty(Option<T> option) {
        return new Condition<>(option, ConditionOperator.MAP_NOT_EMPTY, null, null, null);
    }

    public static <T> Condition<T> mapContainsKey(Option<T> option, String key) {
        return new Condition<>(option, ConditionOperator.MAP_CONTAINS_KEY, key, null, null);
    }

    public static <T> Condition<T> mapContainsKeys(Option<T> option, String... keys) {
        return new Condition<>(option, ConditionOperator.MAP_CONTAINS_KEYS, Arrays.asList(keys), null, null);
    }

    public static <T> Condition<T> extension(Option<T> option, ConditionExtension<T> extension) {
        return new Condition<>(
                Objects.requireNonNull(option, "option"),
                ConditionOperator.EXTENSION,
                null,
                null,
                Objects.requireNonNull(extension, "extension"));
    }

    private static <T extends Comparable<? super T>> Condition<T> compare(
            Option<T> option, ConditionOperator operator, T threshold) {
        return new Condition<>(option, operator, threshold, null, null);
    }

    private static <T extends Comparable<? super T>> Condition<T> compareFields(
            Option<T> option, ConditionOperator operator, Option<T> other) {
        return new Condition<>(option, operator, null, other, null);
    }
}
