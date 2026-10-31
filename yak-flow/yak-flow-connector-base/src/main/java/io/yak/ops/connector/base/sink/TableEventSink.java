package io.yak.ops.connector.base.sink;

import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.core.api.connector.sink.CancellableSinkWriter;
import io.yak.ops.core.api.connector.sink.Committer;
import io.yak.ops.core.api.connector.sink.CommittingSinkWriter;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.sink.SchemaChangeAwareSinkWriter;
import io.yak.ops.core.api.connector.sink.SchemaEvolutionSink;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.StatefulSinkWriter;
import io.yak.ops.core.api.connector.sink.SupportsCommitter;
import io.yak.ops.core.api.connector.sink.SupportsWriterState;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaUtils;
import io.yak.ops.core.schema.SchemaVersionedTableState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Event boundary of table-oriented connector writers.
 *
 * <p>The selected wrappers preserve the delegate's exact checkpoint, commit, cancellation and
 * schema capabilities. Source identities used by existing writer state and metrics stay stable.
 */
public final class TableEventSink {
    private TableEventSink() {}

    public static Sink<Event> of(Sink<TableRecord> delegate, List<TableSinkFactoryContext> tables) {
        Map<TableId, Schema> sources = new LinkedHashMap<>();
        for (TableSinkFactoryContext table : tables) {
            if (sources.putIfAbsent(table.sourceTable(), Schema.from(table.sourceSchema(), null, Map.of(), Map.of()))
                    != null) {
                throw new IllegalArgumentException("Duplicate source table route");
            }
        }
        return of(delegate, tables, (table, schema) -> schema, sources);
    }

    /** Keeps raw source checkpoint schemas distinct from the projected rows consumed by writers. */
    public static Sink<Event> of(
            Sink<TableRecord> delegate,
            List<TableSinkFactoryContext> tables,
            BiFunction<TableId, Schema, Schema> sourceSchemaTransform,
            Map<TableId, Schema> sourceSchemas) {
        Objects.requireNonNull(delegate, "delegate");
        SchemaMapping mapping = new SchemaMapping(tables, sourceSchemaTransform, sourceSchemas);
        int capabilities = (delegate instanceof SupportsWriterState<?, ?> ? 1 : 0)
                | (delegate instanceof SupportsCommitter<?, ?> ? 2 : 0)
                | (delegate instanceof SchemaEvolutionSink ? 4 : 0);
        return switch (capabilities) {
            case 0 -> new EventSink(delegate, mapping);
            case 1 -> new StatefulEventSink(delegate, mapping);
            case 2 -> new CommittingEventSink(delegate, mapping);
            case 3 -> new StatefulCommittingEventSink(delegate, mapping);
            case 4 -> new SchemaEventSink(delegate, mapping);
            case 5 -> new StatefulSchemaEventSink(delegate, mapping);
            case 6 -> new CommittingSchemaEventSink(delegate, mapping);
            case 7 -> new StatefulCommittingSchemaEventSink(delegate, mapping);
            default -> throw new IllegalStateException("Unknown sink capabilities");
        };
    }

    /** Owns the current target shape while the coordinator persists the unprojected source shape. */
    private static final class SchemaMapping {
        private final Map<TableId, TableId> sourceByTarget;
        private final Map<TableId, TableId> targetBySource;
        private final Map<TableId, Schema> initialTargets;
        private final Map<TableId, Schema> targetSchemas;
        private final BiFunction<TableId, Schema, Schema> transform;
        private final List<SchemaVersionedTableState> initialStates;

        private SchemaMapping(
                List<TableSinkFactoryContext> tables,
                BiFunction<TableId, Schema, Schema> transform,
                Map<TableId, Schema> sourceSchemas) {
            this.transform = Objects.requireNonNull(transform, "sourceSchemaTransform");
            Map<TableId, TableId> sources = new LinkedHashMap<>();
            Map<TableId, TableId> targets = new LinkedHashMap<>();
            Map<TableId, Schema> schemas = new LinkedHashMap<>();
            List<SchemaVersionedTableState> initial = new ArrayList<>();
            Map<TableId, Schema> rawSchemas = Map.copyOf(Objects.requireNonNull(sourceSchemas, "sourceSchemas"));
            for (TableSinkFactoryContext table : List.copyOf(Objects.requireNonNull(tables, "tables"))) {
                if (sources.putIfAbsent(table.targetTable(), table.sourceTable()) != null
                        || targets.putIfAbsent(table.sourceTable(), table.targetTable()) != null) {
                    throw new IllegalArgumentException("A table writer requires unique source and target routes");
                }
                schemas.put(table.targetTable(), Schema.from(table.targetSchema(), null, Map.of(), Map.of()));
                Schema raw = rawSchemas.get(table.sourceTable());
                if (raw == null
                        || !transformed(table.sourceTable(), raw)
                                .toTableSchema()
                                .columns()
                                .equals(table.sourceSchema().columns())) {
                    throw new IllegalArgumentException(
                            "Raw source schema does not project to the admitted writer columns");
                }
                initial.add(new SchemaVersionedTableState(
                        table.sourceTable(), table.targetTable(), raw, schemas.get(table.targetTable()), 0));
            }
            if (sources.isEmpty()) {
                throw new IllegalArgumentException("Event sink requires table routes");
            }
            sourceByTarget = Map.copyOf(sources);
            targetBySource = Map.copyOf(targets);
            initialTargets = Map.copyOf(schemas);
            targetSchemas = new LinkedHashMap<>(schemas);
            if (!rawSchemas.keySet().equals(targetBySource.keySet())) {
                throw new IllegalArgumentException("Raw source schema collection contains an unadmitted table");
            }
            initialStates = List.copyOf(initial);
        }

        private Schema transformed(TableId table, Schema schema) {
            return Objects.requireNonNull(transform.apply(table, schema), "Source schema transform returned null");
        }

        private synchronized SchemaChangeRequest plan(
                SchemaEvolutionSink delegate, SourceSchemaChange change, boolean allowDropColumns) {
            Objects.requireNonNull(change, "change");
            TableId target = targetBySource.get(change.tableId());
            if (target == null) {
                throw new IllegalArgumentException("Schema change names an unconfigured source table");
            }
            Schema before = transformed(change.tableId(), change.before());
            Schema after = transformed(change.tableId(), change.after());
            if (before.equals(after)) {
                Schema currentTarget = targetSchemas.get(target);
                return new SchemaChangeRequest(
                        change.eventId(),
                        change.tableId(),
                        target,
                        change.before(),
                        change.after(),
                        currentTarget,
                        currentTarget,
                        List.of());
            }
            SchemaChangeRequest projected = delegate.planSchemaChange(
                    new SourceSchemaChange(change.eventId(), change.tableId(), before, after), allowDropColumns);
            if (!target.equals(projected.targetTable()) || !change.tableId().equals(projected.sourceTable())) {
                throw new IllegalStateException("Schema planner changed the admitted table route");
            }
            return new SchemaChangeRequest(
                    projected.eventId(),
                    projected.sourceTable(),
                    projected.targetTable(),
                    change.before(),
                    change.after(),
                    projected.targetSchemaBefore(),
                    projected.targetSchemaAfter(),
                    projected.targetChanges());
        }

        private SchemaChangeRequest project(SchemaChangeRequest request) {
            validateRoute(request.sourceTable(), request.targetTable());
            return new SchemaChangeRequest(
                    request.eventId(),
                    request.sourceTable(),
                    request.targetTable(),
                    transformed(request.sourceTable(), request.sourceSchemaBefore()),
                    transformed(request.sourceTable(), request.sourceSchemaAfter()),
                    request.targetSchemaBefore(),
                    request.targetSchemaAfter(),
                    request.targetChanges());
        }

        private List<SchemaVersionedTableState> project(List<SchemaVersionedTableState> states) {
            List<SchemaVersionedTableState> projected = new ArrayList<>();
            Set<TableId> targets = new HashSet<>();
            for (SchemaVersionedTableState state : List.copyOf(states)) {
                validateRoute(state.sourceTable(), state.targetTable());
                if (!targets.add(state.targetTable())) {
                    throw new IllegalArgumentException("Duplicate restored target schema");
                }
                projected.add(new SchemaVersionedTableState(
                        state.sourceTable(),
                        state.targetTable(),
                        transformed(state.sourceTable(), state.sourceSchema()),
                        state.targetSchema(),
                        state.version()));
            }
            return List.copyOf(projected);
        }

        private synchronized void completed(SchemaChangeRequest request) {
            validateRoute(request.sourceTable(), request.targetTable());
            targetSchemas.put(request.targetTable(), request.targetSchemaAfter());
        }

        private synchronized void restored(List<SchemaVersionedTableState> states) {
            targetSchemas.clear();
            targetSchemas.putAll(initialTargets);
            for (SchemaVersionedTableState state : states) {
                targetSchemas.put(state.targetTable(), state.targetSchema());
            }
        }

        private void validateRoute(TableId source, TableId target) {
            if (!target.equals(targetBySource.get(source))) {
                throw new IllegalArgumentException("Schema state does not match an admitted table route");
            }
        }
    }

    private static class EventSink implements Sink<Event> {
        private final Sink<TableRecord> delegate;
        private final SchemaMapping mapping;

        private EventSink(Sink<TableRecord> delegate, SchemaMapping mapping) {
            this.delegate = delegate;
            this.mapping = mapping;
        }

        public Sink<TableRecord> delegate() {
            return delegate;
        }

        public SchemaMapping mapping() {
            return mapping;
        }

        @Override
        public SinkWriter<Event> createWriter(WriterInitContext context) throws Exception {
            return wrapWriter(delegate.createWriter(context), mapping);
        }

        @SuppressWarnings("unchecked")
        public StatefulSinkWriter<Event, Object> restoreWriter(
                WriterInitContext context, Collection<Object> restoredState) throws Exception {
            SupportsWriterState<TableRecord, Object> stateful = (SupportsWriterState<TableRecord, Object>) delegate;
            return (StatefulSinkWriter<Event, Object>)
                    wrapWriter(stateful.restoreWriter(context, restoredState), mapping);
        }

        @SuppressWarnings("unchecked")
        public SimpleVersionedSerializer<Object> getWriterStateSerializer() {
            return ((SupportsWriterState<TableRecord, Object>) delegate).getWriterStateSerializer();
        }

        @SuppressWarnings("unchecked")
        public Committer<Object> createCommitter(WriterInitContext context) throws Exception {
            return ((SupportsCommitter<TableRecord, Object>) delegate).createCommitter(context);
        }

        @SuppressWarnings("unchecked")
        public SimpleVersionedSerializer<Object> getCommittableSerializer() {
            return ((SupportsCommitter<TableRecord, Object>) delegate).getCommittableSerializer();
        }
    }

    private interface SchemaSink extends SchemaEvolutionSink {
        Sink<TableRecord> delegate();

        SchemaMapping mapping();

        @Override
        default List<SchemaVersionedTableState> initialSchemaState() {
            return mapping().initialStates;
        }

        @Override
        default MetadataApplier createMetadataApplier() throws Exception {
            return ((SchemaEvolutionSink) delegate()).createMetadataApplier();
        }

        @Override
        default SchemaChangeRequest planSchemaChange(SourceSchemaChange change) {
            return planSchemaChange(change, false);
        }

        @Override
        default SchemaChangeRequest planSchemaChange(SourceSchemaChange change, boolean allowDropColumns) {
            return mapping().plan((SchemaEvolutionSink) delegate(), change, allowDropColumns);
        }

        @Override
        default void schemaChangeCompleted(SchemaChangeRequest request) throws Exception {
            ((SchemaEvolutionSink) delegate()).schemaChangeCompleted(mapping().project(request));
            mapping().completed(request);
        }

        @Override
        default void restoreSchemaState(List<SchemaVersionedTableState> states) throws Exception {
            ((SchemaEvolutionSink) delegate()).restoreSchemaState(mapping().project(states));
            mapping().restored(states);
        }
    }

    private static final class StatefulEventSink extends EventSink implements SupportsWriterState<Event, Object> {
        private StatefulEventSink(Sink<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class CommittingEventSink extends EventSink implements SupportsCommitter<Event, Object> {
        private CommittingEventSink(Sink<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class StatefulCommittingEventSink extends EventSink
            implements SupportsWriterState<Event, Object>, SupportsCommitter<Event, Object> {
        private StatefulCommittingEventSink(Sink<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class SchemaEventSink extends EventSink implements SchemaSink {
        private SchemaEventSink(Sink<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class StatefulSchemaEventSink extends EventSink
            implements SupportsWriterState<Event, Object>, SchemaSink {
        private StatefulSchemaEventSink(Sink<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class CommittingSchemaEventSink extends EventSink
            implements SupportsCommitter<Event, Object>, SchemaSink {
        private CommittingSchemaEventSink(Sink<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class StatefulCommittingSchemaEventSink extends EventSink
            implements SupportsWriterState<Event, Object>, SupportsCommitter<Event, Object>, SchemaSink {
        private StatefulCommittingSchemaEventSink(Sink<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static SinkWriter<Event> wrapWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
        int capabilities = (delegate instanceof StatefulSinkWriter<?, ?> ? 1 : 0)
                | (delegate instanceof CommittingSinkWriter<?, ?> ? 2 : 0)
                | (delegate instanceof SchemaChangeAwareSinkWriter<?> ? 4 : 0)
                | (delegate instanceof CancellableSinkWriter<?> ? 8 : 0);
        return switch (capabilities) {
            case 0 -> new EventWriter(delegate, mapping);
            case 1 -> new StatefulEventWriter(delegate, mapping);
            case 2 -> new CommittingEventWriter(delegate, mapping);
            case 3 -> new StatefulCommittingEventWriter(delegate, mapping);
            case 4 -> new SchemaEventWriter(delegate, mapping);
            case 5 -> new StatefulSchemaEventWriter(delegate, mapping);
            case 6 -> new CommittingSchemaEventWriter(delegate, mapping);
            case 7 -> new StatefulCommittingSchemaEventWriter(delegate, mapping);
            case 8 -> new CancellableEventWriter(delegate, mapping);
            case 9 -> new StatefulCancellableEventWriter(delegate, mapping);
            case 10 -> new CommittingCancellableEventWriter(delegate, mapping);
            case 11 -> new StatefulCommittingCancellableEventWriter(delegate, mapping);
            case 12 -> new SchemaCancellableEventWriter(delegate, mapping);
            case 13 -> new StatefulSchemaCancellableEventWriter(delegate, mapping);
            case 14 -> new CommittingSchemaCancellableEventWriter(delegate, mapping);
            case 15 -> new StatefulCommittingSchemaCancellableEventWriter(delegate, mapping);
            default -> throw new IllegalStateException("Unknown writer capabilities");
        };
    }

    private static class EventWriter implements SinkWriter<Event> {
        private final SinkWriter<TableRecord> delegate;
        private final SchemaMapping mapping;
        private final Map<TableId, Schema> schemas = new LinkedHashMap<>();

        private EventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            this.delegate = delegate;
            this.mapping = mapping;
        }

        @Override
        public void write(Event event, Context context) throws Exception {
            if (event instanceof SchemaChangeEvent change) {
                if (!mapping.sourceByTarget.containsKey(change.tableId())) {
                    throw new IllegalArgumentException("Schema event names an unconfigured sink table");
                }
                Schema current = schemas.get(change.tableId());
                if (change instanceof CreateTableEvent create && current != null) {
                    if (!current.equals(create.getPhysicalSchema())) {
                        throw new IllegalStateException("Repeated CREATE_TABLE conflicts with the input schema");
                    }
                    return;
                }
                schemas.put(change.tableId(), SchemaUtils.applySchemaChangeEvent(current, change));
                return;
            }
            if (!(event instanceof DataChangeEvent change)) {
                throw new UnsupportedOperationException("Unsupported sink event type");
            }
            TableId sourceTable = mapping.sourceByTarget.get(change.tableId());
            Schema schema = schemas.get(change.tableId());
            if (sourceTable == null || schema == null) {
                throw new IllegalStateException("A data event requires its admitted table and preceding CREATE_TABLE");
            }
            validateArity(change.before(), schema);
            validateArity(change.after(), schema);
            switch (change.op()) {
                case INSERT, REPLACE ->
                    delegate.write(
                            image(sourceTable, RowKind.INSERT, change.after(), change.changeEventId(), 0), context);
                case DELETE ->
                    delegate.write(
                            image(sourceTable, RowKind.DELETE, change.before(), change.changeEventId(), 0), context);
                case UPDATE -> {
                    delegate.write(
                            image(sourceTable, RowKind.UPDATE_BEFORE, change.before(), change.changeEventId(), 0),
                            context);
                    delegate.write(
                            image(sourceTable, RowKind.UPDATE_AFTER, change.after(), change.changeEventId(), 1),
                            context);
                }
            }
        }

        private static void validateArity(RowData row, Schema schema) {
            if (row != null && row.getArity() != schema.columns().size()) {
                throw new IllegalArgumentException("Data event row shape does not match its current schema");
            }
        }

        private static TableRecord image(TableId table, RowKind kind, RowData row, ChangeEventId identity, int index) {
            ChangeEventId imageId = identity == null
                    ? null
                    : new ChangeEventId(identity.sourceId(), identity.eventId(), index, identity.imageCount());
            return new TableRecord(table, kind, row, imageId);
        }

        @Override
        public void flush(boolean endOfInput) throws Exception {
            delegate.flush(endOfInput);
        }

        @Override
        public void close() throws Exception {
            delegate.close();
        }

        @SuppressWarnings("unchecked")
        public List<Object> snapshotState(long checkpointId) throws Exception {
            return ((StatefulSinkWriter<TableRecord, Object>) delegate).snapshotState(checkpointId);
        }

        @SuppressWarnings("unchecked")
        public Collection<Object> prepareCommit(long checkpointId) throws Exception {
            return ((CommittingSinkWriter<TableRecord, Object>) delegate).prepareCommit(checkpointId);
        }

        @SuppressWarnings("unchecked")
        public void validateSchemaChange(SchemaChangeRequest request) throws Exception {
            ((SchemaChangeAwareSinkWriter<TableRecord>) delegate).validateSchemaChange(mapping.project(request));
        }

        @SuppressWarnings("unchecked")
        public void onSchemaChange(SchemaChangeRequest request, long schemaVersion) throws Exception {
            ((SchemaChangeAwareSinkWriter<TableRecord>) delegate)
                    .onSchemaChange(mapping.project(request), schemaVersion);
        }

        public void cancel() {
            ((CancellableSinkWriter<?>) delegate).cancel();
        }
    }

    private static final class StatefulEventWriter extends EventWriter implements StatefulSinkWriter<Event, Object> {
        private StatefulEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class CommittingEventWriter extends EventWriter
            implements CommittingSinkWriter<Event, Object> {
        private CommittingEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class StatefulCommittingEventWriter extends EventWriter
            implements StatefulSinkWriter<Event, Object>, CommittingSinkWriter<Event, Object> {
        private StatefulCommittingEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class SchemaEventWriter extends EventWriter implements SchemaChangeAwareSinkWriter<Event> {
        private SchemaEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class StatefulSchemaEventWriter extends EventWriter
            implements StatefulSinkWriter<Event, Object>, SchemaChangeAwareSinkWriter<Event> {
        private StatefulSchemaEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class CommittingSchemaEventWriter extends EventWriter
            implements CommittingSinkWriter<Event, Object>, SchemaChangeAwareSinkWriter<Event> {
        private CommittingSchemaEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class StatefulCommittingSchemaEventWriter extends EventWriter
            implements StatefulSinkWriter<Event, Object>,
                    CommittingSinkWriter<Event, Object>,
                    SchemaChangeAwareSinkWriter<Event> {
        private StatefulCommittingSchemaEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class CancellableEventWriter extends EventWriter implements CancellableSinkWriter<Event> {
        private CancellableEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class StatefulCancellableEventWriter extends EventWriter
            implements StatefulSinkWriter<Event, Object>, CancellableSinkWriter<Event> {
        private StatefulCancellableEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class CommittingCancellableEventWriter extends EventWriter
            implements CommittingSinkWriter<Event, Object>, CancellableSinkWriter<Event> {
        private CommittingCancellableEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class StatefulCommittingCancellableEventWriter extends EventWriter
            implements StatefulSinkWriter<Event, Object>,
                    CommittingSinkWriter<Event, Object>,
                    CancellableSinkWriter<Event> {
        private StatefulCommittingCancellableEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class SchemaCancellableEventWriter extends EventWriter
            implements SchemaChangeAwareSinkWriter<Event>, CancellableSinkWriter<Event> {
        private SchemaCancellableEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class StatefulSchemaCancellableEventWriter extends EventWriter
            implements StatefulSinkWriter<Event, Object>,
                    SchemaChangeAwareSinkWriter<Event>,
                    CancellableSinkWriter<Event> {
        private StatefulSchemaCancellableEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class CommittingSchemaCancellableEventWriter extends EventWriter
            implements CommittingSinkWriter<Event, Object>,
                    SchemaChangeAwareSinkWriter<Event>,
                    CancellableSinkWriter<Event> {
        private CommittingSchemaCancellableEventWriter(SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }

    private static final class StatefulCommittingSchemaCancellableEventWriter extends EventWriter
            implements StatefulSinkWriter<Event, Object>,
                    CommittingSinkWriter<Event, Object>,
                    SchemaChangeAwareSinkWriter<Event>,
                    CancellableSinkWriter<Event> {
        private StatefulCommittingSchemaCancellableEventWriter(
                SinkWriter<TableRecord> delegate, SchemaMapping mapping) {
            super(delegate, mapping);
        }
    }
}
