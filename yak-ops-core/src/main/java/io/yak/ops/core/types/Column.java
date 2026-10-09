package io.yak.ops.core.types;

import java.util.Objects;

/**
 * Named table column. Nullability, character capacity and binary capacity belong to LogicalType.
 */
public record Column(String name, LogicalType dataType) {

    public Column {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dataType, "dataType");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Column name must not be blank");
        }
    }

    /** Projects existing product/Catalog metadata into a single type-owned representation. */
    public Column(String name, LogicalType type, boolean nullable, Integer length) {
        this(name, LogicalTypes.forColumn(Objects.requireNonNull(type, "type"), nullable, length));
    }

    public boolean nullable() {
        return dataType.isNullable();
    }

    /** Returns null when the column's capacity is unknown or unlimited. */
    public Integer length() {
        return switch (dataType) {
            case CharType value -> value.length();
            case VarCharType value -> value.length() == VarCharType.MAX_LENGTH ? null : value.length();
            case BinaryType value -> value.length();
            case VarBinaryType value -> value.length() == VarBinaryType.MAX_LENGTH ? null : value.length();
            default -> null;
        };
    }
}
