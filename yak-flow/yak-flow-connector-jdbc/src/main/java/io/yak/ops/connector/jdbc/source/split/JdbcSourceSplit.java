package io.yak.ops.connector.jdbc.source.split;

import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.data.TableId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * One independently assignable JDBC table-range query and its last emitted integer primary key.
 *
 * <p>A null split column means full-table replay on recovery; no result-set row offset is trusted.
 * Bounds are inclusive and disjoint between sibling splits. The table identity and column order
 * are frozen so a Reader does not depend on later Catalog changes.
 */
public record JdbcSourceSplit(
        String splitId,
        TableId tableId,
        List<String> columns,
        String splitColumn,
        Long lowerBound,
        Long upperBound,
        Long lastEmittedKey)
        implements SourceSplit {

    public JdbcSourceSplit {
        if (splitId == null || splitId.isBlank()) {
            throw new IllegalArgumentException("Split ID must not be blank");
        }
        Objects.requireNonNull(tableId, "tableId");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        if (columns.isEmpty() || columns.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("A JDBC split requires nonblank column names");
        }
        if (new HashSet<>(columns).size() != columns.size()) {
            throw new IllegalArgumentException("Duplicate JDBC column names");
        }
        if ((lowerBound == null) != (upperBound == null)) {
            throw new IllegalArgumentException("Both range bounds must be present or absent");
        }
        if (splitColumn == null && (lowerBound != null || lastEmittedKey != null)) {
            throw new IllegalArgumentException("Unkeyed splits cannot track numeric bounds or offsets");
        }
        if (lowerBound != null && lowerBound > upperBound) {
            throw new IllegalArgumentException("Invalid inclusive split bounds");
        }
        if (lastEmittedKey != null
                && lowerBound != null
                && (lastEmittedKey < lowerBound || lastEmittedKey > upperBound)) {
            throw new IllegalArgumentException("Split checkpoint position is outside the range");
        }
    }

    /** Returns a detached position snapshot without changing the original assigned split. */
    public JdbcSourceSplit withLastEmittedKey(Long key) {
        return new JdbcSourceSplit(splitId, tableId, columns, splitColumn, lowerBound, upperBound, key);
    }
}
