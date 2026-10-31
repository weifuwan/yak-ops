package io.yak.ops.core.event;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaUtils;
import io.yak.ops.core.types.LogicalType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One source schema transition and its already-planned target changes.
 *
 * <p>The event ID must be stable across replay (for example, a source log position plus table
 * identity). The source connector supplies the before/after source schema; planning supplies the
 * before/after target schema and the ordered, validated target events. The runtime never guesses
 * a target mapping or translates arbitrary SQL.
 *
 * <p>A source projection can hide a schema change from a writer. Such an internal writer request
 * has equal input and target shapes with no target events; the coordinator retains the original
 * raw source transition and its replay identity. Target field names can differ from raw source
 * names, so connectors own source-column attribution and destructive-change authorization.
 */
public record SchemaChangeRequest(
        String eventId,
        TableId sourceTable,
        TableId targetTable,
        Schema sourceSchemaBefore,
        Schema sourceSchemaAfter,
        Schema targetSchemaBefore,
        Schema targetSchemaAfter,
        List<SchemaChangeEvent> targetChanges)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public SchemaChangeRequest {
        eventId = Objects.requireNonNull(eventId, "eventId").trim();
        sourceTable = Objects.requireNonNull(sourceTable, "sourceTable");
        targetTable = Objects.requireNonNull(targetTable, "targetTable");
        sourceSchemaBefore = Objects.requireNonNull(sourceSchemaBefore, "sourceSchemaBefore");
        sourceSchemaAfter = Objects.requireNonNull(sourceSchemaAfter, "sourceSchemaAfter");
        targetSchemaBefore = Objects.requireNonNull(targetSchemaBefore, "targetSchemaBefore");
        targetSchemaAfter = Objects.requireNonNull(targetSchemaAfter, "targetSchemaAfter");
        targetChanges = List.copyOf(Objects.requireNonNull(targetChanges, "targetChanges"));
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("Schema change event ID must not be blank");
        }
        if (sourceSchemaBefore.equals(sourceSchemaAfter)
                && (!targetSchemaBefore.equals(targetSchemaAfter) || !targetChanges.isEmpty())) {
            throw new IllegalArgumentException("An unchanged input schema cannot request target DDL");
        }
        if (targetChanges.isEmpty() && !targetSchemaBefore.equals(targetSchemaAfter)) {
            throw new IllegalArgumentException("Target schema was changed without executable target events");
        }
        Schema expectedTarget = targetSchemaBefore;
        for (SchemaChangeEvent change : targetChanges) {
            Objects.requireNonNull(change, "target change");
            if (!targetTable.equals(change.tableId())) {
                throw new IllegalArgumentException("Target schema event identity does not match the request");
            }
            if (change instanceof AddColumnEvent add) {
                expectedTarget = addColumns(expectedTarget, add);
            } else if (change instanceof AlterColumnTypeEvent alter) {
                expectedTarget = alterColumnTypes(expectedTarget, alter);
            } else if (change instanceof DropColumnEvent drop) {
                // A transform may rename a source field. The connector planner verifies source
                // ownership and DROP authorization; this contract validates the actual target.
                expectedTarget = SchemaUtils.applySchemaChangeEvent(expectedTarget, drop);
            } else {
                throw new UnsupportedOperationException("Unsupported runtime target schema event: " + change.getType());
            }
        }
        if (!expectedTarget.equals(targetSchemaAfter)) {
            throw new IllegalArgumentException("Target change events do not produce the declared target schema");
        }
    }

    private static Schema addColumns(Schema schema, AddColumnEvent event) {
        List<PhysicalColumn> columns = new ArrayList<>(schema.columns());
        int firstOffset = 0;
        for (AddColumnEvent.ColumnWithPosition addition : event.addedColumns()) {
            PhysicalColumn column = addition.column();
            if (columns.stream().anyMatch(existing -> existing.name().equals(column.name()))) {
                throw new IllegalArgumentException("Schema event adds an existing column");
            }
            int index =
                    switch (addition.position()) {
                        case FIRST -> firstOffset++;
                        case LAST -> columns.size();
                        case BEFORE, AFTER -> {
                            int reference = -1;
                            for (int candidate = 0; candidate < columns.size(); candidate++) {
                                if (columns.get(candidate).name().equals(addition.referenceColumn())) {
                                    reference = candidate;
                                    break;
                                }
                            }
                            if (reference < 0) {
                                throw new IllegalArgumentException("Schema event references an unknown column");
                            }
                            yield addition.position() == AddColumnEvent.ColumnPosition.BEFORE
                                    ? reference
                                    : reference + 1;
                        }
                    };
            columns.add(index, column);
        }
        return new Schema(columns, schema.primaryKeys(), schema.comment());
    }

    private static Schema alterColumnTypes(Schema schema, AlterColumnTypeEvent event) {
        List<PhysicalColumn> columns = new ArrayList<>(schema.columns());
        for (java.util.Map.Entry<String, LogicalType> entry :
                event.typeMapping().entrySet()) {
            int index = -1;
            for (int candidate = 0; candidate < columns.size(); candidate++) {
                if (columns.get(candidate).name().equals(entry.getKey())) {
                    index = candidate;
                    break;
                }
            }
            if (index < 0) {
                throw new IllegalArgumentException("Schema event alters an unknown column");
            }
            PhysicalColumn previous = columns.get(index);
            LogicalType expected = event.oldTypeMapping().get(entry.getKey());
            if (expected != null && !expected.equals(previous.type())) {
                throw new IllegalArgumentException("Schema event contains a stale column type");
            }
            columns.set(index, previous.copy(entry.getValue()));
        }
        return new Schema(columns, schema.primaryKeys(), schema.comment());
    }

    /** Diagnostics expose identity and operation count, never SQL or default expressions. */
    @Override
    public String toString() {
        return "SchemaChangeRequest{eventId='" + eventId + "', sourceTable=" + sourceTable + ", targetTable="
                + targetTable + ", targetChangeCount=" + targetChanges.size() + "}";
    }
}
