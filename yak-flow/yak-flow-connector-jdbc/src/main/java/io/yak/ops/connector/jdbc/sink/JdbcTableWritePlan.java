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
 * Defines an immutable source-to-target table mapping and its resolved JDBC write contract.
 *
 * <p>Target column order determines positional SQL binding. Source positions select fields
 * with explicit lossless conversion to target logical types. UPDATE_BEFORE/DELETE carry
 * only the converted target key values, whereas INSERT/UPDATE_AFTER carry the full target schema.
 * Planning and projection do not open JDBC connections.
 */
public record JdbcTableWritePlan(
        TableId sourceTable,
        TableId targetTable,
        TableSchema schema,
        JdbcWriteMode writeMode,
        TableSchema sourceSchema,
        List<Integer> sourcePositions) {

    /** Creates a single-table write route with a target-ordered identity projection. */
    public JdbcTableWritePlan(TableId sourceTable, TableId targetTable, TableSchema schema, JdbcWriteMode writeMode) {
        this(sourceTable, targetTable, schema, writeMode, schema, identityPositions(schema));
    }

    /**
     * Maps target column names to source column names; unmapped target columns use their own name.
     * Only explicitly supported lossless type conversions are performed.
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
            if (!JdbcColumnValueConverter.supports(source, target)) {
                throw new IllegalArgumentException(
                        "JDBC mapping cannot safely convert source to target field: " + target.name());
            }
        }
        if (writeMode == JdbcWriteMode.RETRACT) {
            JdbcSinkSchemaValidator.validateExactRow(sourceSchema, schema);
            for (int index = 0; index < sourcePositions.size(); index++) {
                if (sourcePositions.get(index) != index) {
                    throw new IllegalArgumentException("JDBC RETRACT requires an exact full-row identity mapping");
                }
            }
        }
        if (writeMode == JdbcWriteMode.UPSERT) {
            // A snapshot Chunk Key chooses a read range; it is not a physical row identity.
            // Every destination key must map bijectively to a declared source primary key,
            // including when source and destination field names differ.
            if (schema.primaryKeys().isEmpty()
                    || sourceSchema.primaryKeys().isEmpty()
                    || schema.primaryKeys().size() != sourceSchema.primaryKeys().size()) {
                throw new IllegalArgumentException("JDBC UPSERT requires row identity on both source and target");
            }
            Set<String> sourceKeys = new HashSet<>();
            for (String targetKey : schema.primaryKeys()) {
                int targetPosition = -1;
                for (int index = 0; index < schema.columnCount(); index++) {
                    if (schema.column(index).name().equals(targetKey)) {
                        targetPosition = index;
                        break;
                    }
                }
                if (targetPosition < 0) {
                    throw new IllegalArgumentException("Missing destination primary-key column: " + targetKey);
                }
                String sourceKey =
                        sourceSchema.column(sourcePositions.get(targetPosition)).name();
                if (!sourceSchema.primaryKeys().contains(sourceKey) || !sourceKeys.add(sourceKey)) {
                    throw new IllegalArgumentException(
                            "JDBC UPSERT destination primary key must map to a source write identity key: "
                                    + targetKey);
                }
            }
            if (!sourceKeys.equals(new HashSet<>(sourceSchema.primaryKeys()))) {
                throw new IllegalArgumentException("JDBC UPSERT does not preserve the full source row identity");
            }
        }
    }

    /**
     * Selects parameterized INSERT or native UPSERT SQL using the target column order.
     *
     * @param dialect database-specific DML implementation
     * @return SQL for this route's configured write mode
     */
    public String sql(JdbcDialect dialect) {
        Objects.requireNonNull(dialect, "dialect");
        return switch (writeMode) {
            case APPEND -> dialect.insertSql(targetTable, schema);
            case UPSERT -> dialect.upsertSql(targetTable, schema);
            case REPLACE -> dialect.replaceSql(targetTable, schema);
            case RETRACT -> dialect.insertSql(targetTable, schema);
        };
    }

    /**
     * Builds a replacement route while retaining existing field mappings by name.
     * Newly introduced target fields use the same-named source field and are rejected before DDL
     * if that source field is not present or its logical type cannot be written.
     */
    public JdbcTableWritePlan withSchemas(TableSchema nextSourceSchema, TableSchema nextTargetSchema) {
        Objects.requireNonNull(nextSourceSchema, "nextSourceSchema");
        Objects.requireNonNull(nextTargetSchema, "nextTargetSchema");
        Map<String, String> mappings = new java.util.LinkedHashMap<>();
        Set<String> nextNames = new HashSet<>();
        nextTargetSchema.columns().forEach(column -> nextNames.add(column.name()));
        for (int index = 0; index < schema.columnCount(); index++) {
            Column target = schema.column(index);
            if (!nextNames.contains(target.name())) continue;
            Column source = sourceSchema.column(sourcePositions.get(index));
            mappings.put(target.name(), source.name());
        }
        for (Column target : nextTargetSchema.columns()) {
            if (!mappings.containsKey(target.name())
                    && nextSourceSchema.columns().stream()
                            .anyMatch(column -> column.name().equals(target.name()))) {
                mappings.put(target.name(), target.name());
            }
        }
        return new JdbcTableWritePlan(
                sourceTable, targetTable, nextTargetSchema, writeMode, nextSourceSchema, mappings);
    }

    /**
     * Compares the before-image key projection with the after-image target row.
     *
     * <p>Rows are already detached by {@link #project(RowData, RowKind)}. Key comparisons
     * do not use SQL string rendering and also support binary primary keys.
     *
     * @param beforeKeys key projection from the UPDATE_BEFORE row
     * @param afterRow complete target-ordered UPDATE_AFTER row
     * @return true if all primary-key components, including binary keys, are unchanged
     */
    public boolean hasSamePrimaryKey(RowData beforeKeys, RowData afterRow) {
        Objects.requireNonNull(beforeKeys, "beforeKeys");
        Objects.requireNonNull(afterRow, "afterRow");
        if (beforeKeys.getArity() != schema.primaryKeys().size() || afterRow.getArity() != schema.columnCount()) {
            throw new IllegalArgumentException("JDBC UPDATE row/key arity does not match the write plan");
        }
        for (int index = 0; index < schema.primaryKeys().size(); index++) {
            Object oldKey = beforeKeys.getField(index);
            Object newKey = afterRow.getField(targetIndex(schema.primaryKeys().get(index)));
            if (oldKey == null || newKey == null) {
                throw new IllegalArgumentException("JDBC UPDATE primary keys must not be null");
            }
            if (!Objects.deepEquals(oldKey, newKey)) {
                return false;
            }
        }
        return true;
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
     * Copies and maps one source row before it enters the sole JDBC batch buffer.
     *
     * <p>UPSERT DELETE/UPDATE_BEFORE emit only non-null primary keys, while RETRACT emits
     * the complete before-image for exact-row comparison. INSERT/UPDATE_AFTER project
     * all target columns and reject null UPSERT keys. Caller-owned mutable byte arrays are
     * detached through the internal RowData representation.
     *
     * @param row source-ordered row values
     * @param kind the source event's mutation kind
     * @return a detached target-ordered row or key projection
     * @throws IllegalArgumentException if arity, key or write-mode constraints are violated
     */
    public RowData project(RowData row, RowKind kind) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(kind, "kind");
        if (row.getArity() != sourceSchema.columnCount()) {
            throw new IllegalArgumentException("JDBC input arity does not match the source schema");
        }
        if ((writeMode == JdbcWriteMode.APPEND || writeMode == JdbcWriteMode.REPLACE) && kind != RowKind.INSERT) {
            throw new UnsupportedOperationException("APPEND and REPLACE modes accept INSERT records only");
        }
        if (writeMode != JdbcWriteMode.RETRACT && (kind == RowKind.DELETE || kind == RowKind.UPDATE_BEFORE)) {
            GenericRowData keys = new GenericRowData(schema.primaryKeys().size());
            int index = 0;
            for (String key : schema.primaryKeys()) {
                int targetIndex = targetIndex(key);
                int sourcePosition = sourcePositions.get(targetIndex);
                Objects.requireNonNull(row.getField(sourcePosition), "JDBC retract primary key must not be null");
                Object value = JdbcColumnValueConverter.convert(
                        row, sourcePosition, sourceSchema.column(sourcePosition), schema.column(targetIndex));
                keys.setField(index++, Objects.requireNonNull(value, "JDBC retract primary key must not be null"));
            }
            return keys;
        }
        GenericRowData projected = new GenericRowData(schema.columnCount());
        for (int index = 0; index < projected.getArity(); index++) {
            int sourcePosition = sourcePositions.get(index);
            projected.setField(
                    index,
                    JdbcColumnValueConverter.convert(
                            row, sourcePosition, sourceSchema.column(sourcePosition), schema.column(index)));
        }
        if (writeMode == JdbcWriteMode.UPSERT) {
            for (String key : schema.primaryKeys()) {
                if (projected.isNullAt(targetIndex(key))) {
                    throw new IllegalArgumentException("JDBC UPSERT primary keys must not be null");
                }
            }
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
