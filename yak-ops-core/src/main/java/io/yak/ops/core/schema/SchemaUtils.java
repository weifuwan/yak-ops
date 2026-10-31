package io.yak.ops.core.schema;

import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.AlterColumnTypeEvent;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DropColumnEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies supported schema events to immutable descriptions without executing database DDL. */
public final class SchemaUtils {
    private SchemaUtils() {}

    /**
     * Applies an event and returns an independent schema value.
     *
     * <p>A CREATE_TABLE starts a schema. Other events require an existing schema. Unimplemented
     * event types fail rather than silently treating a schema modification as a no-op.
     */
    public static Schema applySchemaChangeEvent(Schema schema, SchemaChangeEvent event) {
        Objects.requireNonNull(event, "event");
        if (event instanceof CreateTableEvent create) {
            return create.getPhysicalSchema();
        }
        Objects.requireNonNull(schema, "schema");
        if (event instanceof AddColumnEvent add) {
            List<PhysicalColumn> columns = new ArrayList<>(schema.columns());
            for (AddColumnEvent.ColumnWithPosition addition : add.addedColumns()) {
                int index =
                        switch (addition.position()) {
                            case FIRST -> 0;
                            case LAST -> columns.size();
                            case BEFORE -> indexOf(columns, addition.referenceColumn());
                            case AFTER -> indexOf(columns, addition.referenceColumn()) + 1;
                        };
                if (columns.stream()
                        .anyMatch(
                                column -> column.name().equals(addition.column().name()))) {
                    throw new IllegalArgumentException(
                            "Column already exists: " + addition.column().name());
                }
                columns.add(index, addition.column());
            }
            return schema.withColumns(columns);
        }
        if (event instanceof AlterColumnTypeEvent alter) {
            for (String name : alter.typeMapping().keySet()) {
                PhysicalColumn current = schema.column(name)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown column for type change: " + name));
                if (alter.oldTypeMapping().containsKey(name)
                        && !current.type().equals(alter.oldTypeMapping().get(name))) {
                    throw new IllegalArgumentException("Column type changed before applying event: " + name);
                }
            }
            List<PhysicalColumn> columns = schema.columns().stream()
                    .map(column -> alter.typeMapping().containsKey(column.name())
                            ? column.copy(alter.typeMapping().get(column.name()))
                            : column)
                    .toList();
            return schema.withColumns(columns);
        }
        if (event instanceof DropColumnEvent drop) {
            for (String name : drop.droppedColumnNames()) {
                if (schema.primaryKeys().contains(name)) {
                    throw new IllegalArgumentException("Cannot drop primary-key column: " + name);
                }
                PhysicalColumn current = schema.column(name)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown column for DROP COLUMN: " + name));
                if (!current.type().equals(drop.expectedColumnTypes().get(name))) {
                    throw new IllegalArgumentException("Column type changed before DROP COLUMN: " + name);
                }
            }
            List<PhysicalColumn> columns = new ArrayList<>(schema.columns());
            columns.removeIf(column -> drop.droppedColumnNames().contains(column.name()));
            return schema.withColumns(columns);
        }
        throw new UnsupportedOperationException("Unsupported schema event: " + event.getType());
    }

    /**
     * Derives ordered logical schema events for a source transition.
     *
     * <p>After a type change a source may invalidate its previous native column properties.
     * The complete new physical metadata remains in the coordinated source schema checkpoint;
     * event operators use the logical shape. All other unrepresentable metadata changes fail.
     */
    public static List<SchemaChangeEvent> diff(io.yak.ops.core.data.TableId table, Schema before, Schema after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        if (!before.primaryKeys().equals(after.primaryKeys()) || !Objects.equals(before.comment(), after.comment())) {
            throw new UnsupportedOperationException("Changing primary keys or table comments is not supported");
        }
        List<SchemaChangeEvent> changes = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        java.util.Map<String, io.yak.ops.core.types.LogicalType> removedTypes = new java.util.LinkedHashMap<>();
        java.util.Map<String, io.yak.ops.core.types.LogicalType> changedTypes = new java.util.LinkedHashMap<>();
        java.util.Map<String, io.yak.ops.core.types.LogicalType> previousTypes = new java.util.LinkedHashMap<>();
        for (PhysicalColumn column : before.columns()) {
            PhysicalColumn replacement = after.column(column.name()).orElse(null);
            if (replacement == null) {
                removed.add(column.name());
                removedTypes.put(column.name(), column.type());
            } else if (!column.type().equals(replacement.type())) {
                changedTypes.put(column.name(), replacement.type());
                previousTypes.put(column.name(), column.type());
            }
        }
        Schema current = before;
        if (!removed.isEmpty()) {
            SchemaChangeEvent drop = new DropColumnEvent(table, removed, removedTypes);
            changes.add(drop);
            current = applySchemaChangeEvent(current, drop);
        }
        if (!changedTypes.isEmpty()) {
            SchemaChangeEvent alter = new AlterColumnTypeEvent(table, changedTypes, previousTypes);
            changes.add(alter);
            current = applySchemaChangeEvent(current, alter);
        }
        for (int index = 0; index < after.columns().size(); index++) {
            PhysicalColumn column = after.columns().get(index);
            if (before.column(column.name()).isEmpty()) {
                AddColumnEvent.ColumnWithPosition position = index == 0
                        ? AddColumnEvent.first(column)
                        : AddColumnEvent.after(
                                column, after.columns().get(index - 1).name());
                SchemaChangeEvent add = new AddColumnEvent(table, List.of(position));
                changes.add(add);
                current = applySchemaChangeEvent(current, add);
            }
        }
        List<PhysicalColumn> comparableColumns = new ArrayList<>();
        for (PhysicalColumn column : current.columns()) {
            PhysicalColumn expected = after.column(column.name()).orElseThrow();
            if (changedTypes.containsKey(column.name()) && expected.properties() == null) {
                comparableColumns.add(new PhysicalColumn(column.name(), column.type(), column.comment(), null));
            } else {
                comparableColumns.add(column);
            }
        }
        if (!current.withColumns(comparableColumns).equals(after)) {
            throw new UnsupportedOperationException(
                    "Source schema transition cannot be represented without metadata loss");
        }
        return List.copyOf(changes);
    }

    private static int indexOf(List<PhysicalColumn> columns, String name) {
        for (int index = 0; index < columns.size(); index++) {
            if (columns.get(index).name().equals(name)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Unknown reference column: " + name);
    }
}
