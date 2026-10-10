package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * A resolved route from one source table to one target table.
 *
 * <p>Source positions are ordered by target columns. Mapping and type validation happen
 * before the Writer opens a connection; neither JDBC nor the Runtime owns schema mapping.
 * A DELETE or UPDATE_BEFORE only needs non-null key fields in the source row.
 */
public record JdbcTableWritePlan(
        TableId sourceTable,
        TableId targetTable,
        TableSchema schema,
        JdbcWriteMode writeMode,
        TableSchema sourceSchema,
        List<Integer> sourcePositions) {

    /** Preserves the original target-ordered single-table contract. */
    public JdbcTableWritePlan(TableId sourceTable, TableId targetTable, TableSchema schema, JdbcWriteMode writeMode) {
        this(sourceTable, targetTable, schema, writeMode, schema, identityPositions(schema));
    }

    /**
     * Maps target column names to source column names; unmapped target columns use their own name.
     * No implicit type coercion is performed.
     */
    public JdbcTableWritePlan(
            TableId sourceTable,
            TableId targetTable,
            TableSchema schema,
            JdbcWriteMode writeMode,
            TableSchema sourceSchema,
            Map<String, String> targetToSourceColumns) {
        this(
                sourceTable,
                targetTable,
                schema,
                writeMode,
                sourceSchema,
                positions(schema, sourceSchema, targetToSourceColumns));
    }

    public JdbcTableWritePlan {
        Objects.requireNonNull(sourceTable, "sourceTable");
        Objects.requireNonNull(targetTable, "targetTable");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(writeMode, "writeMode");
        Objects.requireNonNull(sourceSchema, "sourceSchema");
        sourcePositions = List.copyOf(Objects.requireNonNull(sourcePositions, "sourcePositions"));
        if (schema.columnCount() != sourcePositions.size()) {
            throw new IllegalArgumentException("JDBC mapping must cover all target columns");
        }
        for (int index = 0; index < schema.columnCount(); index++) {
            Column target = schema.column(index);
            int position = sourcePositions.get(index);
            if (position < 0 || position >= sourceSchema.columnCount()) {
                throw new IllegalArgumentException("JDBC source field position is outside the schema");
            }
            Column source = sourceSchema.column(position);
            if (!target.dataType().isResolved()
                    || !source.dataType().isResolved()
                    || target.dataType().getTypeRoot() != source.dataType().getTypeRoot()) {
                throw new IllegalArgumentException("JDBC mapping requires matching resolved logical types");
            }
        }
        if (writeMode == JdbcWriteMode.UPSERT && schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("JDBC UPSERT requires primary keys");
        }
    }

    public String sql(JdbcDialect dialect) {
        Objects.requireNonNull(dialect, "dialect");
        return switch (writeMode) {
            case APPEND -> dialect.insertSql(targetTable, schema);
            case UPSERT -> dialect.upsertSql(targetTable, schema);
        };
    }

    /** Returns an immutable-schema binder for the target's ordered primary-key columns. */
    public TableSchema keySchema() {
        List<Column> keys = new ArrayList<>(schema.primaryKeys().size());
        for (String key : schema.primaryKeys()) {
            Column column = schema.columns().stream()
                    .filter(candidate -> candidate.name().equals(key))
                    .findFirst()
                    .orElseThrow();
            keys.add(new Column(column.name(), column.dataType().copy(false)));
        }
        return new TableSchema(keys, List.of());
    }

    /**
     * Detaches values when records enter the unique JDBC buffer. Key retractions use only
     * the before-image's key fields, preserving primary-key changes.
     */
    public RowData project(RowData row, RowKind kind) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(kind, "kind");
        if (row.getArity() != sourceSchema.columnCount()) {
            throw new IllegalArgumentException("JDBC input arity does not match the source schema");
        }
        if (writeMode == JdbcWriteMode.APPEND && kind != RowKind.INSERT) {
            throw new UnsupportedOperationException("APPEND mode accepts INSERT records only");
        }
        if (kind == RowKind.DELETE || kind == RowKind.UPDATE_BEFORE) {
            GenericRowData keys = new GenericRowData(schema.primaryKeys().size());
            int index = 0;
            for (String key : schema.primaryKeys()) {
                int targetIndex = targetIndex(key);
                Object value = row.getField(sourcePositions.get(targetIndex));
                keys.setField(index++, Objects.requireNonNull(value, "JDBC retract primary key must not be null"));
            }
            return keys;
        }
        GenericRowData projected = new GenericRowData(schema.columnCount());
        for (int index = 0; index < projected.getArity(); index++) {
            projected.setField(index, row.getField(sourcePositions.get(index)));
        }
        return projected;
    }

    private int targetIndex(String name) {
        for (int index = 0; index < schema.columnCount(); index++) {
            if (schema.column(index).name().equals(name)) {
                return index;
            }
        }
        throw new IllegalStateException("Missing JDBC target primary key");
    }

    private static List<Integer> identityPositions(TableSchema schema) {
        return IntStream.range(0, Objects.requireNonNull(schema, "schema").columnCount())
                .boxed()
                .toList();
    }

    private static List<Integer> positions(TableSchema target, TableSchema source, Map<String, String> mappings) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mappings, "mappings");
        Set<String> targetNames = new HashSet<>();
        for (Column column : target.columns()) {
            targetNames.add(column.name());
        }
        if (!targetNames.containsAll(mappings.keySet())) {
            throw new IllegalArgumentException("JDBC mapping names an unknown target column");
        }
        List<Integer> positions = new ArrayList<>(target.columnCount());
        for (Column column : target.columns()) {
            String name = mappings.getOrDefault(column.name(), column.name());
            int position = -1;
            for (int index = 0; index < source.columnCount(); index++) {
                if (source.column(index).name().equals(name)) {
                    position = index;
                    break;
                }
            }
            if (position < 0) {
                throw new IllegalArgumentException("JDBC source field not found for target " + column.name());
            }
            positions.add(position);
        }
        return positions;
    }
}
