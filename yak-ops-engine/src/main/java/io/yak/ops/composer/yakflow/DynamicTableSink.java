package io.yak.ops.composer.yakflow;

import io.yak.ops.composer.definition.PipelineOptions;
import io.yak.ops.core.api.connector.sink.CancellableSinkWriter;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.sink.SchemaChangeAwareSinkWriter;
import io.yak.ops.core.api.connector.sink.SchemaEvolutionSink;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.SupportsCommitter;
import io.yak.ops.core.api.connector.sink.SupportsWriterState;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.ChangeEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.function.HashFunctionProvider;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaVersionedTableState;
import io.yak.ops.core.sink.DataSink;
import io.yak.ops.core.sink.EventSinkProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Admits factory-created table writers through the existing checkpointed schema coordinator.
 * Discovery routes and raw schemas are restored from the schema checkpoint; this sink stores
 * no separate durable table registry and accepts no stateful/XA delegates.
 */
final class DynamicTableSink implements DataSink, Sink<Event>, SchemaEvolutionSink {
    @FunctionalInterface
    interface TableFactory {
        Registration create(TableId source, Schema raw, SchemaVersionedTableState restored);
    }

    static final class Registration {
        private final Sink<Event> sink;
        private final SchemaEvolutionSink schemas;
        private final SchemaChangeRequest discovery;

        Registration(DataSink sink, SchemaChangeRequest discovery) {
            this.sink = sink.getEventSinkProvider().getSink();
            if (!(this.sink instanceof SchemaEvolutionSink evolution)
                    || this.sink instanceof SupportsWriterState<?, ?>
                    || this.sink instanceof SupportsCommitter<?, ?>) {
                throw new IllegalArgumentException("Dynamic tables require stateless schema-aware UPSERT writers");
            }
            schemas = evolution;
            this.discovery = discovery;
        }
    }

    private final Map<TableId, Registration> tables = new LinkedHashMap<>();
    private final Map<TableId, TableId> routes = new LinkedHashMap<>();
    private final Map<TableId, TableId> sourcesByTarget = new LinkedHashMap<>();
    private final List<SchemaVersionedTableState> initial;
    private final Function<TableId, TableId> naming;
    private final TableFactory factory;

    DynamicTableSink(Map<TableId, DataSink> initial, Function<TableId, TableId> naming, TableFactory factory) {
        this.naming = Objects.requireNonNull(naming, "naming");
        this.factory = Objects.requireNonNull(factory, "factory");
        List<SchemaVersionedTableState> states = new ArrayList<>();
        initial.forEach((source, sink) -> {
            Registration registration = new Registration(sink, null);
            List<SchemaVersionedTableState> state = registration.schemas.initialSchemaState();
            if (state.size() != 1 || !state.getFirst().sourceTable().equals(source)) {
                throw new IllegalArgumentException("A dynamic writer factory must own exactly one source route");
            }
            install(source, state.getFirst().targetTable(), registration, false);
            states.add(state.getFirst());
        });
        if (states.isEmpty()) throw new IllegalArgumentException("Dynamic discovery requires initial admitted tables");
        this.initial = List.copyOf(states);
    }

    private void install(TableId source, TableId target, Registration registration, boolean discovered) {
        if (discovered && !target.equals(naming.apply(source)))
            throw new IllegalArgumentException("Table route differs from the published mapping rule");
        TableId owner = sourcesByTarget.get(target);
        if (owner != null && !owner.equals(source))
            throw new IllegalArgumentException("Dynamic naming maps multiple sources to one target");
        if (!tables.containsKey(source) && tables.size() >= PipelineOptions.MAX_TABLES) {
            throw new IllegalArgumentException("Dynamic discovery exceeds the 100-table limit");
        }
        tables.put(source, registration);
        routes.put(source, target);
        sourcesByTarget.put(target, source);
    }

    synchronized TableId target(TableId source) {
        TableId target = routes.get(source);
        if (target == null) throw new IllegalArgumentException("Event references a table not admitted by discovery");
        return target;
    }

    @Override
    public EventSinkProvider getEventSinkProvider() {
        return () -> this;
    }

    @Override
    public boolean supportsTableDiscovery() {
        return true;
    }

    @Override
    public List<SchemaVersionedTableState> initialSchemaState() {
        return initial;
    }

    @Override
    public MetadataApplier getMetadataApplier() {
        try {
            return createMetadataApplier();
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot create dynamic metadata applier", failure);
        }
    }

    @Override
    public synchronized MetadataApplier createMetadataApplier() throws Exception {
        return tables.values().iterator().next().schemas.createMetadataApplier();
    }

    @Override
    public HashFunctionProvider<DataChangeEvent> getDataChangeEventHashFunctionProvider() {
        // Mutable keys still preserve ordering: every mutation of a table selects one writer.
        return (table, schema) -> event -> table.hashCode() & Integer.MAX_VALUE;
    }

    @Override
    public synchronized SchemaChangeRequest planSchemaChange(SourceSchemaChange change, boolean allowDropColumns) {
        Registration registration = tables.get(change.tableId());
        if (change.newlyAdded()) {
            if (registration == null) {
                TableId target = naming.apply(change.tableId());
                if (sourcesByTarget.containsKey(target))
                    throw new IllegalArgumentException("Discovered target collides with an admitted route");
                registration = factory.create(change.tableId(), change.after(), null);
                install(change.tableId(), target, registration, true);
            }
            if (registration.discovery == null
                    || !registration.discovery.eventId().equals(change.eventId())
                    || !registration.discovery.sourceSchemaAfter().equals(change.after())) {
                throw new IllegalArgumentException("Conflicting new-table admission replay");
            }
            return registration.discovery;
        }
        if (registration == null) throw new IllegalArgumentException("Schema change refers to an unadmitted table");
        return registration.schemas.planSchemaChange(change, allowDropColumns);
    }

    @Override
    public synchronized void schemaChangeCompleted(SchemaChangeRequest request) throws Exception {
        if (!request.newlyAdded()) tables.get(request.sourceTable()).schemas.schemaChangeCompleted(request);
    }

    @Override
    public synchronized void restoreSchemaState(List<SchemaVersionedTableState> states) throws Exception {
        if (states.size() < initial.size() || states.size() > PipelineOptions.MAX_TABLES) {
            throw new IllegalArgumentException("Invalid dynamic table checkpoint size");
        }
        java.util.Set<TableId> seen = new java.util.HashSet<>();
        for (SchemaVersionedTableState state : states) {
            if (!seen.add(state.sourceTable())) throw new IllegalArgumentException("Duplicate restored source table");
            Registration registration = tables.get(state.sourceTable());
            if (registration == null) {
                registration = factory.create(state.sourceTable(), state.sourceSchema(), state);
                install(state.sourceTable(), state.targetTable(), registration, true);
            } else if (!state.targetTable().equals(target(state.sourceTable()))) {
                throw new IllegalArgumentException("Restored target route changed identity");
            }
            registration.schemas.restoreSchemaState(List.of(state));
        }
        if (!seen.containsAll(tables.keySet()))
            throw new IllegalArgumentException("Checkpoint omits admitted source routes");
    }

    @Override
    public synchronized SinkWriter<Event> createWriter(WriterInitContext context) throws Exception {
        return new DynamicWriter(context);
    }

    private final class DynamicWriter
            implements SinkWriter<Event>, SchemaChangeAwareSinkWriter<Event>, CancellableSinkWriter<Event> {
        private final WriterInitContext context;
        private final Map<TableId, SinkWriter<Event>> writers = new LinkedHashMap<>();
        private volatile List<SinkWriter<Event>> active = List.of();
        private volatile boolean cancelled;

        private DynamicWriter(WriterInitContext context) throws Exception {
            this.context = context;
            try {
                for (Map.Entry<TableId, Registration> entry : tables.entrySet()) add(entry.getKey(), entry.getValue());
            } catch (Exception failure) {
                try {
                    close();
                } catch (Exception cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }

        private void add(TableId source, Registration registration) throws Exception {
            SinkWriter<Event> writer = registration.sink.createWriter(context);
            if (!(writer instanceof SchemaChangeAwareSinkWriter<?>)) {
                writer.close();
                throw new IllegalArgumentException("Dynamic writer lacks schema change support");
            }
            writers.put(source, writer);
            active = List.copyOf(writers.values());
            if (cancelled && writer instanceof CancellableSinkWriter<?> cancellable) cancellable.cancel();
        }

        @Override
        public void write(Event event, Context context) throws Exception {
            if (!(event instanceof ChangeEvent change))
                throw new IllegalArgumentException("Unsupported dynamic sink event");
            TableId source;
            synchronized (DynamicTableSink.this) {
                source = sourcesByTarget.get(change.tableId());
            }
            SinkWriter<Event> writer = writers.get(source);
            if (writer == null)
                throw new IllegalStateException("Target writer was not installed before its data stream");
            writer.write(event, context);
        }

        @Override
        public void flush(boolean endOfInput) throws Exception {
            for (SinkWriter<Event> writer : active) writer.flush(endOfInput);
        }

        @Override
        public void validateSchemaChange(SchemaChangeRequest request) throws Exception {
            if (request.newlyAdded()) {
                synchronized (DynamicTableSink.this) {
                    Registration registration = tables.get(request.sourceTable());
                    if (registration == null || !request.equals(registration.discovery)) {
                        throw new IllegalArgumentException(
                                "New writer was not planned by its published discovery rule");
                    }
                }
            } else schemaWriter(request.sourceTable()).validateSchemaChange(request);
        }

        @Override
        public void onSchemaChange(SchemaChangeRequest request, long version) throws Exception {
            if (request.newlyAdded()) {
                if (!writers.containsKey(request.sourceTable())) {
                    synchronized (DynamicTableSink.this) {
                        add(request.sourceTable(), tables.get(request.sourceTable()));
                    }
                }
            } else schemaWriter(request.sourceTable()).onSchemaChange(request, version);
        }

        @SuppressWarnings("unchecked")
        private SchemaChangeAwareSinkWriter<Event> schemaWriter(TableId source) {
            return (SchemaChangeAwareSinkWriter<Event>) Objects.requireNonNull(writers.get(source), "schema writer");
        }

        @Override
        public void cancel() {
            cancelled = true;
            for (SinkWriter<Event> writer : active)
                if (writer instanceof CancellableSinkWriter<?> cancellable) cancellable.cancel();
        }

        @Override
        public void close() throws Exception {
            Exception failure = null;
            for (SinkWriter<Event> writer : active) {
                try {
                    writer.close();
                } catch (Exception error) {
                    if (failure == null) failure = error;
                    else failure.addSuppressed(error);
                }
            }
            if (failure != null) throw failure;
        }
    }
}
