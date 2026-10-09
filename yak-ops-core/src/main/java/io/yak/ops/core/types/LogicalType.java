package io.yak.ops.core.types;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Immutable database-independent type semantics, independent of JDBC metadata or product columns.
 *
 * <p>Every resolved type owns its nullability and any precision/length parameters. Unresolved
 * decimal metadata must be resolved before the JDBC-to-RowData conversion boundary.
 */
public abstract sealed class LogicalType implements Serializable
        permits BasicType,
                DecimalType,
                UnresolvedDecimalType,
                CharType,
                VarCharType,
                BinaryType,
                VarBinaryType,
                TimeType,
                TimestampType,
                ZonedTimestampType {

    private final boolean nullable;
    private final LogicalTypeRoot typeRoot;

    protected LogicalType(boolean nullable, LogicalTypeRoot typeRoot) {
        this.nullable = nullable;
        this.typeRoot = Objects.requireNonNull(typeRoot, "typeRoot");
    }

    public final boolean isNullable() {
        return nullable;
    }

    public final LogicalTypeRoot getTypeRoot() {
        return typeRoot;
    }

    public boolean isResolved() {
        return true;
    }

    public abstract LogicalType copy(boolean nullable);

    public final LogicalType copy() {
        return copy(nullable);
    }

    public abstract String asSerializableString();

    public String asSummaryString() {
        return asSerializableString();
    }

    public List<LogicalType> getChildren() {
        return List.of();
    }

    public abstract Class<?> getDefaultConversion();

    protected final String withNullability(String representation) {
        return nullable ? representation : representation + " NOT NULL";
    }

    protected Object parameters() {
        return null;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        LogicalType that = (LogicalType) other;
        return nullable == that.nullable
                && typeRoot == that.typeRoot
                && Objects.equals(parameters(), that.parameters());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getClass(), nullable, typeRoot, parameters());
    }

    @Override
    public final String toString() {
        return asSummaryString();
    }
}
