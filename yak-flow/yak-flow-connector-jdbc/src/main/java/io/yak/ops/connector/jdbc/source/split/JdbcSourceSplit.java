package io.yak.ops.connector.jdbc.source.split;

import io.yak.ops.connector.jdbc.database.query.JdbcReadQuery;
import io.yak.ops.connector.jdbc.source.JdbcIncrementalRange;
import io.yak.ops.connector.jdbc.source.JdbcReadOptions;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.data.TableId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Identifies one bounded JDBC split, its frozen projection, and its optional emitted-key cursor.
 *
 * <p>Sibling numeric ranges have inclusive, nonoverlapping bounds. A missing split column means
 * full-table replay rather than relying on unstable ResultSet offsets. The assigned table identity
 * and projection remain fixed across serialization and checkpoint restoration.
 */
public record JdbcSourceSplit(
        String splitId,
        TableId tableId,
        List<String> columns,
        String splitColumn,
        Long lowerBound,
        Long upperBound,
        Long lastEmittedKey,
        String schemaFingerprint,
        JdbcIncrementalRange incrementalRange,
        JdbcReadOptions readOptions)
        implements SourceSplit {

    public JdbcSourceSplit(
            String splitId,
            TableId tableId,
            List<String> columns,
            String splitColumn,
            Long lowerBound,
            Long upperBound,
            Long lastEmittedKey,
            String schemaFingerprint,
            JdbcIncrementalRange incrementalRange) {
        this(
                splitId,
                tableId,
                columns,
                splitColumn,
                lowerBound,
                upperBound,
                lastEmittedKey,
                schemaFingerprint,
                incrementalRange,
                null);
    }

    /** Constructs a normal full-table split without an incremental cursor filter. */
    public JdbcSourceSplit(
            String splitId,
            TableId tableId,
            List<String> columns,
            String splitColumn,
            Long lowerBound,
            Long upperBound,
            Long lastEmittedKey,
            String schemaFingerprint) {
        this(splitId, tableId, columns, splitColumn, lowerBound, upperBound, lastEmittedKey, schemaFingerprint, null);
    }

    public JdbcSourceSplit {
        if (splitId == null || splitId.isBlank()) {
            throw new IllegalArgumentException("Split ID must not be blank");
        }
        Objects.requireNonNull(tableId, "tableId");
        if (schemaFingerprint == null || !schemaFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("JDBC split requires a SHA-256 schema fingerprint");
        }
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

    /**
     * Columns read by JDBC. An unselected primary key is appended solely for checkpoint progress;
     * it must not become part of the emitted RowData.
     */
    public List<String> readColumns() {
        return readColumns(columns, splitColumn, readOptions == null ? null : readOptions.where());
    }

    /**
     * Adds a missing split-key column to the physical query without changing the emitted projection.
     *
     * @param projectedColumns ordered columns exposed to downstream RowData
     * @param splitColumn optional key needed for checkpoint progress
     * @return physical read columns, with an extra key only when necessary
     */
    public static List<String> readColumns(List<String> projectedColumns, String splitColumn) {
        return readColumns(projectedColumns, splitColumn, null);
    }

    /** Includes predicate columns in the physical schema fingerprint without emitting hidden columns. */
    public static List<String> readColumns(List<String> projectedColumns, String splitColumn, String where) {
        List<String> selected = new ArrayList<>(projectedColumns);
        if (splitColumn != null && !selected.contains(splitColumn)) selected.add(splitColumn);
        for (String column : JdbcReadQuery.referencedColumns(where)) {
            if (!selected.contains(column)) selected.add(column);
        }
        return List.copyOf(selected);
    }

    /** Returns a detached position snapshot without changing the original assigned split. */
    public JdbcSourceSplit withLastEmittedKey(Long key) {
        return new JdbcSourceSplit(
                splitId,
                tableId,
                columns,
                splitColumn,
                lowerBound,
                upperBound,
                key,
                schemaFingerprint,
                incrementalRange,
                readOptions);
    }
}
