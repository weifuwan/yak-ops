package io.yak.ops.core.data;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Array-backed RowData for JDBC batches and general-purpose YakFlow operators.
 *
 * <p>Fields may be changed during construction but must not be mutated after the record enters
 * a fetch queue. Mutable binary and legacy timestamp values are defensively copied on access.
 * Future binary/columnar implementations may implement RowData without changing Connector APIs.
 */
public final class GenericRowData implements RowData {

    private final Object[] fields;

    public GenericRowData(int arity) {
        if (arity < 0) {
            throw new IllegalArgumentException("Row arity must not be negative");
        }
        fields = new Object[arity];
    }

    public GenericRowData(List<?> values) {
        Objects.requireNonNull(values, "values");
        fields = new Object[values.size()];
        for (int index = 0; index < values.size(); index++) {
            fields[index] = copyMutableValue(values.get(index));
        }
    }

    public static GenericRowData of(Object... values) {
        return new GenericRowData(Arrays.asList(Objects.requireNonNull(values, "values")));
    }

    public void setField(int position, Object value) {
        fields[position] = copyMutableValue(value);
    }

    @Override
    public int getArity() {
        return fields.length;
    }

    @Override
    public boolean isNullAt(int position) {
        return fields[position] == null;
    }

    @Override
    public Object getField(int position) {
        return copyMutableValue(fields[position]);
    }

    public GenericRowData copy() {
        return new GenericRowData(Arrays.asList(fields));
    }

    /** Copies only mutable internal field families; Connector-specific values require conversion. */
    private static Object copyMutableValue(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes.clone();
        }
        if (value instanceof Timestamp timestamp) {
            return (Timestamp) timestamp.clone();
        }
        if (value instanceof java.util.Date date) {
            return (java.util.Date) date.clone();
        }
        if (value instanceof Object[] array) {
            Object[] copy = new Object[array.length];
            for (int index = 0; index < array.length; index++) {
                copy[index] = copyMutableValue(array[index]);
            }
            return copy;
        }
        return value;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof GenericRowData other && Arrays.deepEquals(fields, other.fields);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(fields);
    }

    @Override
    public String toString() {
        return Arrays.deepToString(fields);
    }
}
