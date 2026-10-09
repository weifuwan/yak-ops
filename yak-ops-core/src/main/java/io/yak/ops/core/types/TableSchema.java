package io.yak.ops.core.types;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Logical table schema with ordered columns and ordered primary-key names.
 *
 * <p>It is a value model rather than an active database catalog or a product task definition.
 */
public record TableSchema(List<Column> columns, List<String> primaryKeys) {

    public TableSchema {
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        primaryKeys = List.copyOf(Objects.requireNonNull(primaryKeys, "primaryKeys"));
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Table schema columns must not be empty");
        }
        Set<String> names = new HashSet<>();
        for (Column column : columns) {
            if (!names.add(column.name())) {
                throw new IllegalArgumentException("Duplicate schema column: " + column.name());
            }
        }
        Set<String> keys = new HashSet<>();
        for (String key : primaryKeys) {
            if (!names.contains(key) || !keys.add(key)) {
                throw new IllegalArgumentException("Invalid schema primary key: " + key);
            }
        }
    }

    public int columnCount() {
        return columns.size();
    }

    public Column column(int index) {
        return columns.get(index);
    }
}
