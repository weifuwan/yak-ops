package io.yak.ops.core.types;

import java.util.Objects;

/** One column of a table schema, retaining database column name and logical type. */
public record Column(String name, LogicalType dataType, boolean nullable, Integer length) {

    public Column {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dataType, "dataType");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Column name must not be blank");
        }
        if (length != null && length < 0) {
            throw new IllegalArgumentException("Column length must not be negative");
        }
    }
}
