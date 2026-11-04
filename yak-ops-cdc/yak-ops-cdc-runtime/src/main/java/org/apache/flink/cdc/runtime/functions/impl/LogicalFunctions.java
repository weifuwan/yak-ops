package org.apache.flink.cdc.runtime.functions.impl;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Supplier;

/** Logical built-in functions. */
public class LogicalFunctions {

    public static Boolean and(Boolean left, Boolean right) {
        if (Boolean.FALSE.equals(left) || Boolean.FALSE.equals(right)) {
            return false;
        }
        if (left == null || right == null) {
            return null;
        }
        return true;
    }

    public static Boolean or(Boolean left, Boolean right) {
        if (Boolean.TRUE.equals(left) || Boolean.TRUE.equals(right)) {
            return true;
        }
        if (left == null || right == null) {
            return null;
        }
        return false;
    }

    public static Boolean and(Boolean left, Supplier<Boolean> rightSupplier) {
        if (Boolean.FALSE.equals(left)) {
            return false;
        }
        Boolean right = rightSupplier.get();
        return and(left, right);
    }

    public static Boolean or(Boolean left, Supplier<Boolean> rightSupplier) {
        if (Boolean.TRUE.equals(left)) {
            return true;
        }
        Boolean right = rightSupplier.get();
        return or(left, right);
    }

    public static Boolean not(Boolean value) {
        return value == null ? null : !value;
    }

    public static boolean isNull(Object value) {
        return value == null;
    }

    public static boolean isNotNull(Object value) {
        return value != null;
    }

    public static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    public static boolean isNotTrue(Boolean value) {
        return !Boolean.TRUE.equals(value);
    }

    public static boolean isFalse(Boolean value) {
        return Boolean.FALSE.equals(value);
    }

    public static boolean isNotFalse(Boolean value) {
        return !Boolean.FALSE.equals(value);
    }

    public static boolean isUnknown(Boolean value) {
        return value == null;
    }

    public static boolean isNotUnknown(Boolean value) {
        return value != null;
    }

    public static Object coalesce(Object... objects) {
        for (Object item : objects) {
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    public static <T> T ifNull(T value, T replacement) {
        return value != null ? value : replacement;
    }

    public static <T> T nullIf(T value, Object comparison) {
        return valuesEqualForNullIf(value, comparison) ? null : value;
    }

    private static boolean valuesEqualForNullIf(Object value, Object comparison) {
        if (value == null || comparison == null) {
            return false;
        }
        if (!(value instanceof Number) || !(comparison instanceof Number)) {
            return Objects.deepEquals(value, comparison);
        }

        Number left = (Number) value;
        Number right = (Number) comparison;
        if (left instanceof Double
                || right instanceof Double
                || left instanceof Float
                || right instanceof Float) {
            return left.doubleValue() == right.doubleValue();
        }
        if (left instanceof BigDecimal || right instanceof BigDecimal) {
            return toBigDecimal(left).compareTo(toBigDecimal(right)) == 0;
        }
        return left.longValue() == right.longValue();
    }

    private static BigDecimal toBigDecimal(Number number) {
        return number instanceof BigDecimal
                ? (BigDecimal) number
                : BigDecimal.valueOf(number.longValue());
    }
}
