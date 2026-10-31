package io.yak.ops.flow.runtime.schema;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.AlterColumnTypeEvent;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.event.DropColumnEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaVersionedTableState;
import io.yak.ops.core.types.LogicalType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mailbox-independent state model for source and target schema versions.
 *
 * <p>The coordinator serializes changes. This class still synchronizes every transition because
 * checkpoint snapshots and diagnostics can be requested from another runtime thread.
 */
public final class SchemaManager {

    private static final int MAX_REPLAY_HISTORY = 256;

    private final Map<TableId, SchemaManagerSnapshot.TableState> tables = new LinkedHashMap<>();
    private final Map<TableId, SchemaChangeRequest> pending = new LinkedHashMap<>();
    private final Map<TableId, Long> pendingVersions = new LinkedHashMap<>();

    /** Freezes the initial raw source/target shapes before any checkpoint can capture offsets. */
    public synchronized void initialize(List<SchemaVersionedTableState> initialStates) {
        if (!tables.isEmpty() || !pending.isEmpty()) {
            throw new IllegalStateException("Schema manager is already initialized");
        }
        Map<TableId, SchemaManagerSnapshot.TableState> initial = new LinkedHashMap<>();
        java.util.Set<TableId> sources = new java.util.HashSet<>();
        for (SchemaVersionedTableState state : List.copyOf(initialStates)) {
            if (state.version() != 0
                    || !sources.add(state.sourceTable())
                    || initial.putIfAbsent(
                                    state.targetTable(),
                                    new SchemaManagerSnapshot.TableState(
                                            state.sourceTable(),
                                            state.targetTable(),
                                            state.sourceSchema(),
                                            state.targetSchema(),
                                            0,
                                            Map.of()))
                            != null) {
                throw new IllegalArgumentException("Initial schema routes must be unique and have version zero");
            }
        }
        tables.putAll(initial);
    }

    /** Checks restored route coverage without replacing old checkpoint schemas with live metadata. */
    public synchronized void validateRestoredRoutes(List<SchemaVersionedTableState> admitted) {
        validateRestoredRoutes(admitted, false);
    }

    /** Dynamic sinks validate additional checkpointed routes against their immutable naming rules. */
    public synchronized void validateRestoredRoutes(List<SchemaVersionedTableState> admitted, boolean discovered) {
        if (admitted.isEmpty()) return;
        java.util.Set<TableId> expected = new java.util.HashSet<>();
        for (SchemaVersionedTableState route : List.copyOf(admitted)) {
            SchemaManagerSnapshot.TableState restored = tables.get(route.targetTable());
            if (!expected.add(route.targetTable())
                    || restored == null
                    || !restored.sourceTable().equals(route.sourceTable())) {
                throw new IllegalStateException("Checkpoint lacks the admitted initial source/target schema route");
            }
        }
        if (!discovered && !tables.keySet().equals(expected)) {
            throw new IllegalStateException("Checkpoint schema routes differ from the admitted table collection");
        }
    }

    /** Begins a target transition or reports an already committed, identical replay. */
    public synchronized Change begin(SchemaChangeRequest request) {
        Objects.requireNonNull(request, "request");
        String fingerprint = fingerprint(request);
        TableId target = request.targetTable();
        SchemaManagerSnapshot.TableState state = tables.get(target);
        if (state == null) {
            state = new SchemaManagerSnapshot.TableState(
                    request.sourceTable(),
                    target,
                    request.sourceSchemaBefore(),
                    request.targetSchemaBefore(),
                    0,
                    Map.of());
            tables.put(target, state);
        }
        if (!state.sourceTable().equals(request.sourceTable())) {
            throw new IllegalStateException("Schema request changes the source identity for an existing target");
        }
        String appliedFingerprint = state.appliedEventFingerprints().get(request.eventId());
        if (appliedFingerprint != null) {
            if (!appliedFingerprint.equals(fingerprint)) {
                throw new IllegalStateException("Schema event ID was reused for a different transition");
            }
            return new Change(true, state.version());
        }
        SchemaChangeRequest active = pending.get(target);
        if (active != null) {
            if (active.eventId().equals(request.eventId())
                    && fingerprint(active).equals(fingerprint)) {
                return new Change(false, pendingVersions.get(target));
            }
            throw new IllegalStateException("Another schema transition is already active for this target");
        }
        if (!state.sourceSchema().equals(request.sourceSchemaBefore())
                || !state.targetSchema().equals(request.targetSchemaBefore())) {
            throw new IllegalStateException("Schema request is stale relative to the committed schema version");
        }
        long nextVersion = Math.addExact(state.version(), 1);
        pending.put(target, request);
        pendingVersions.put(target, nextVersion);
        return new Change(false, nextVersion);
    }

    /** Commits both source and target shapes only after DDL and every Writer update succeed. */
    public synchronized long complete(SchemaChangeRequest request) {
        Objects.requireNonNull(request, "request");
        TableId target = request.targetTable();
        SchemaChangeRequest active = pending.get(target);
        if (active == null || !active.equals(request)) {
            throw new IllegalStateException("Schema transition is not pending for this target");
        }
        SchemaManagerSnapshot.TableState previous = tables.get(target);
        long nextVersion = pendingVersions.get(target);
        Map<String, String> history = new LinkedHashMap<>(previous.appliedEventFingerprints());
        history.put(request.eventId(), fingerprint(request));
        while (history.size() > MAX_REPLAY_HISTORY) {
            history.remove(history.keySet().iterator().next());
        }
        tables.put(
                target,
                new SchemaManagerSnapshot.TableState(
                        request.sourceTable(),
                        target,
                        request.sourceSchemaAfter(),
                        request.targetSchemaAfter(),
                        nextVersion,
                        history));
        pending.remove(target);
        pendingVersions.remove(target);
        return nextVersion;
    }

    /** Aborts a pre-DDL plan. Once DDL starts, the owning Job must fail and recover from a checkpoint. */
    public synchronized void abort(SchemaChangeRequest request) {
        Objects.requireNonNull(request, "request");
        TableId target = request.targetTable();
        if (request.equals(pending.get(target))) {
            pending.remove(target);
            pendingVersions.remove(target);
        }
    }

    public synchronized long currentVersion(TableId target) {
        SchemaManagerSnapshot.TableState state = tables.get(Objects.requireNonNull(target, "target"));
        return state == null ? 0 : state.version();
    }

    public synchronized Optional<SchemaManagerSnapshot.TableState> state(TableId target) {
        return Optional.ofNullable(tables.get(Objects.requireNonNull(target, "target")));
    }

    /** Reads committed source state without exposing a pending target transition. */
    public synchronized Optional<SchemaManagerSnapshot.TableState> sourceState(TableId source) {
        Objects.requireNonNull(source, "source");
        return tables.values().stream()
                .filter(state -> state.sourceTable().equals(source))
                .findFirst();
    }

    /** A checkpoint may only include a committed version, never a partially applied DDL. */
    public synchronized SchemaManagerSnapshot snapshot() {
        if (!pending.isEmpty()) {
            throw new IllegalStateException("Cannot checkpoint while a schema change is in progress");
        }
        return new SchemaManagerSnapshot(tables.values().stream()
                .sorted(java.util.Comparator.comparing(
                        table -> table.targetTable().toString()))
                .toList());
    }

    /** Replaces all state from a completed checkpoint before tasks are started. */
    public synchronized void restore(SchemaManagerSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!pending.isEmpty()) {
            throw new IllegalStateException("Cannot restore SchemaManager with an active transition");
        }
        Map<TableId, SchemaManagerSnapshot.TableState> restored = new LinkedHashMap<>();
        for (SchemaManagerSnapshot.TableState state : snapshot.tables()) {
            if (restored.putIfAbsent(state.targetTable(), state) != null) {
                throw new IllegalArgumentException("Duplicate target in schema manager checkpoint");
            }
        }
        tables.clear();
        tables.putAll(restored);
    }

    private static String fingerprint(SchemaChangeRequest request) {
        StringBuilder value = new StringBuilder();
        appendTableId(value, request.sourceTable());
        appendTableId(value, request.targetTable());
        if (request.newlyAdded()) appendToken(value, "new-table");
        appendSchema(value, request.sourceSchemaBefore());
        appendSchema(value, request.sourceSchemaAfter());
        appendSchema(value, request.targetSchemaBefore());
        appendSchema(value, request.targetSchemaAfter());
        appendToken(value, Integer.toString(request.targetChanges().size()));
        for (SchemaChangeEvent event : request.targetChanges()) {
            appendToken(value, event.getType().name());
            appendTableId(value, event.tableId());
            if (event instanceof AddColumnEvent add) {
                appendToken(value, Integer.toString(add.addedColumns().size()));
                for (AddColumnEvent.ColumnWithPosition addition : add.addedColumns()) {
                    appendColumn(value, addition.column());
                    appendToken(value, addition.position().name());
                    appendToken(value, addition.referenceColumn());
                }
            } else if (event instanceof AlterColumnTypeEvent alter) {
                appendToken(value, Integer.toString(alter.typeMapping().size()));
                alter.typeMapping().keySet().stream().sorted().forEach(name -> {
                    appendToken(value, name);
                    LogicalType oldType = alter.oldTypeMapping().get(name);
                    appendToken(value, oldType == null ? null : oldType.asSerializableString());
                    appendToken(value, alter.typeMapping().get(name).asSerializableString());
                });
            } else if (event instanceof DropColumnEvent drop) {
                appendToken(value, Integer.toString(drop.droppedColumnNames().size()));
                for (String name : drop.droppedColumnNames()) {
                    appendToken(value, name);
                    appendToken(value, drop.expectedColumnTypes().get(name).asSerializableString());
                }
            }
        }
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void appendTableId(StringBuilder value, TableId table) {
        appendToken(value, table.catalog());
        appendToken(value, table.schema());
        appendToken(value, table.table());
    }

    private static void appendSchema(StringBuilder value, Schema schema) {
        appendToken(value, schema.comment());
        appendToken(value, Integer.toString(schema.columns().size()));
        schema.columns().forEach(column -> appendColumn(value, column));
        appendToken(value, Integer.toString(schema.primaryKeys().size()));
        schema.primaryKeys().forEach(key -> appendToken(value, key));
    }

    private static void appendColumn(StringBuilder value, PhysicalColumn column) {
        appendToken(value, column.name());
        appendToken(value, column.type().asSerializableString());
        appendToken(value, column.comment());
        ColumnProperties properties = column.properties();
        appendToken(value, properties == null ? null : properties.defaultExpression());
        appendToken(
                value,
                properties == null || properties.autoIncrement() == null
                        ? null
                        : properties.autoIncrement().toString());
        appendToken(
                value,
                properties == null || properties.generated() == null
                        ? null
                        : properties.generated().toString());
        appendToken(value, properties == null ? null : properties.onUpdateExpression());
        appendToken(
                value,
                properties == null || properties.defaultGenerated() == null
                        ? null
                        : properties.defaultGenerated().toString());
        appendToken(value, properties == null ? null : properties.nativeType());
    }

    /** Length-prefixes every value so arbitrary quoted identifiers cannot collide in the fingerprint. */
    private static void appendToken(StringBuilder value, String token) {
        if (token == null) {
            value.append("-1:");
        } else {
            value.append(token.length()).append(':').append(token);
        }
    }

    public record Change(boolean duplicate, long version) {
        public Change {
            if (version < 0) {
                throw new IllegalArgumentException("Schema version must not be negative");
            }
        }
    }
}
