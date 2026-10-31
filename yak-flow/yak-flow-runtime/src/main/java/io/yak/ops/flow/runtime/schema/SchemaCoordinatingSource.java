package io.yak.ops.flow.runtime.schema;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaUtils;
import io.yak.ops.flow.runtime.checkpoint.CheckpointDeclinedException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Coordinates native schema Events before releasing their following rows into the pipeline.
 *
 * <p>A poll may emit a schema event and later rows together. Those records stay on the owning
 * mailbox until the existing coordinator has drained prior rows, applied DDL and adopted every
 * writer plan. A checkpoint declines while that buffer is nonempty, so advanced source offsets
 * can never overtake undelivered records. Connector split and enumerator formats are unchanged.
 */
public final class SchemaCoordinatingSource<SplitT extends SourceSplit, StateT>
        implements Source<Event, SplitT, StateT> {
    private final Source<Event, SplitT, StateT> source;
    private Map<TableId, Schema> initialSchemas;

    public SchemaCoordinatingSource(Source<Event, SplitT, StateT> source, Map<TableId, Schema> initialSchemas) {
        this.source = Objects.requireNonNull(source, "source");
        this.initialSchemas = Map.copyOf(Objects.requireNonNull(initialSchemas, "initialSchemas"));
        if (this.initialSchemas.isEmpty()) {
            throw new IllegalArgumentException("An Event source requires admitted table metadata");
        }
    }

    @Override
    public void restoreDiscoveredTables(Map<TableId, Schema> restored) {
        source.restoreDiscoveredTables(restored);
        initialSchemas = Map.copyOf(restored);
    }

    @Override
    public Boundedness getBoundedness() {
        return source.getBoundedness();
    }

    @Override
    public void validateParallelism(int parallelism) {
        source.validateParallelism(parallelism);
    }

    @Override
    public SplitEnumerator<SplitT, StateT> createEnumerator(SplitEnumeratorContext<SplitT> context) throws Exception {
        return source.createEnumerator(context);
    }

    @Override
    public SplitEnumerator<SplitT, StateT> restoreEnumerator(SplitEnumeratorContext<SplitT> context, StateT state)
            throws Exception {
        return source.restoreEnumerator(context, state);
    }

    @Override
    public SimpleVersionedSerializer<SplitT> getSplitSerializer() {
        return source.getSplitSerializer();
    }

    @Override
    public SimpleVersionedSerializer<StateT> getEnumeratorCheckpointSerializer() {
        return source.getEnumeratorCheckpointSerializer();
    }

    @Override
    public SourceReader<Event, SplitT> createReader(SourceReaderContext context) throws Exception {
        return new CoordinatingReader(context);
    }

    private final class CoordinatingReader implements SourceReader<Event, SplitT> {
        private final SourceReaderContext context;
        private final SourceReader<Event, SplitT> reader;
        private final Map<TableId, Schema> schemas = new LinkedHashMap<>();
        private final Set<TableId> announced = new HashSet<>();
        private final Queue<Event> pending = new ArrayDeque<>();
        private final Map<TableId, Queue<SchemaAcknowledgement>> acknowledged = new HashMap<>();
        private CompletableFuture<Long> schemaResult;
        private Schema schemaAfter;
        private boolean sourceEnded;

        private CoordinatingReader(SourceReaderContext context) throws Exception {
            this.context = Objects.requireNonNull(context, "context");
            for (Map.Entry<TableId, Schema> entry : initialSchemas.entrySet()) {
                schemas.put(
                        entry.getKey(),
                        context.restoredSourceSchema(entry.getKey()).orElse(entry.getValue()));
            }
            reader = source.createReader(new SourceReaderContext() {
                @Override
                public Configuration getConfiguration() {
                    return context.getConfiguration();
                }

                @Override
                public int getIndexOfSubtask() {
                    return context.getIndexOfSubtask();
                }

                @Override
                public int currentParallelism() {
                    return context.currentParallelism();
                }

                @Override
                public void reportCompletedSplits(int count) {
                    context.reportCompletedSplits(count);
                }

                @Override
                public void sendSplitRequest() {
                    context.sendSplitRequest();
                }

                @Override
                public void sendSourceEventToCoordinator(SourceEvent event) {
                    context.sendSourceEventToCoordinator(event);
                }

                @Override
                public Optional<Schema> restoredSourceSchema(TableId table) {
                    return context.restoredSourceSchema(table);
                }

                @Override
                public long sourceSchemaVersion(TableId table) {
                    return context.sourceSchemaVersion(table);
                }

                @Override
                public CompletionStage<Long> sendSourceSchemaChange(SourceSchemaChange change) {
                    SchemaAcknowledgement transition =
                            new SchemaAcknowledgement(change.tableId(), change.before(), change.after());
                    return acknowledge(context.sendSourceSchemaChange(change), transition);
                }

                @Override
                public CompletionStage<Long> sendSchemaChangeRequestToCoordinator(SchemaChangeRequest request) {
                    SchemaAcknowledgement transition = new SchemaAcknowledgement(
                            request.sourceTable(), request.sourceSchemaBefore(), request.sourceSchemaAfter());
                    return acknowledge(context.sendSchemaChangeRequestToCoordinator(request), transition);
                }
            });
        }

        private CompletionStage<Long> acknowledge(CompletionStage<Long> result, SchemaAcknowledgement transition) {
            return result.thenApply(version -> {
                synchronized (acknowledged) {
                    acknowledged
                            .computeIfAbsent(transition.table, ignored -> new ArrayDeque<>())
                            .add(transition);
                }
                return version;
            });
        }

        @Override
        public void start() throws Exception {
            reader.start();
        }

        @Override
        public InputStatus pollNext(ReaderOutput<Event> output) throws Exception {
            if (!drain(output)) return InputStatus.NOTHING_AVAILABLE;
            if (sourceEnded) return InputStatus.END_OF_INPUT;
            InputStatus status = reader.pollNext(event -> pending.add(detach(Objects.requireNonNull(event, "event"))));
            sourceEnded = status == InputStatus.END_OF_INPUT;
            if (!drain(output)) return InputStatus.NOTHING_AVAILABLE;
            return status;
        }

        private boolean drain(ReaderOutput<Event> output) throws Exception {
            while (!pending.isEmpty()) {
                Event event = pending.element();
                if (event instanceof CreateTableEvent create) {
                    Schema expected = schemas.get(create.tableId());
                    if (expected == null) {
                        if (!consumeAcknowledgement(create))
                            throw new IllegalArgumentException("Source announced an unadmitted table");
                        expected = schemas.get(create.tableId());
                    }
                    boolean first = announced.add(create.tableId());
                    Schema currentCatalog = initialSchemas.get(create.tableId());
                    boolean restoredCatalogDifference = first
                            && !expected.equals(currentCatalog)
                            && currentCatalog != null
                            && currentCatalog.equals(create.getPhysicalSchema());
                    if (!expected.equals(create.getPhysicalSchema()) && !restoredCatalogDifference) {
                        throw new IllegalStateException(
                                "Source metadata conflicts with its admitted or recovered schema");
                    }
                    // The catalog may have advanced since the checkpoint's source offset.
                    event = new CreateTableEvent(create.tableId(), expected);
                } else if (event instanceof SchemaChangeEvent change) {
                    requireAnnounced(change.tableId());
                    if (schemaResult != null) {
                        if (!schemaResult.isDone()) return false;
                        schemaResult.join();
                        schemas.put(change.tableId(), schemaAfter);
                        schemaResult = null;
                        schemaAfter = null;
                    } else if (!consumeAcknowledgement(change)) {
                        if (context.currentParallelism() != 1) {
                            throw new UnsupportedOperationException(
                                    "Native Event schema evolution requires source parallelism one; parallel readers require connector-coordinated metadata");
                        }
                        Schema before = schemas.get(change.tableId());
                        schemaAfter = SchemaUtils.applySchemaChangeEvent(before, change);
                        long version = Math.addExact(context.sourceSchemaVersion(change.tableId()), 1);
                        SourceSchemaChange request = new SourceSchemaChange(
                                "native-event:" + version, change.tableId(), before, schemaAfter);
                        schemaResult = context.sendSourceSchemaChange(request).toCompletableFuture();
                        continue;
                    }
                } else if (event instanceof DataChangeEvent data) {
                    requireAnnounced(data.tableId());
                    synchronized (acknowledged) {
                        Queue<SchemaAcknowledgement> transitions = remainingAcknowledgements(data.tableId());
                        if (transitions != null && !transitions.isEmpty()) {
                            throw new IllegalStateException(
                                    "Source emitted rows before its acknowledged schema Events");
                        }
                    }
                    validateRow(data.before(), schemas.get(data.tableId()));
                    validateRow(data.after(), schemas.get(data.tableId()));
                } else {
                    throw new UnsupportedOperationException("Unsupported source Event type");
                }
                output.collect(event);
                pending.remove();
            }
            return true;
        }

        /** Existing table connectors publish the Events of their already coordinated DDL once. */
        private boolean consumeAcknowledgement(SchemaChangeEvent event) {
            synchronized (acknowledged) {
                Queue<SchemaAcknowledgement> transitions = remainingAcknowledgements(event.tableId());
                if (transitions == null || transitions.isEmpty()) return false;
                SchemaAcknowledgement transition = transitions.element();
                if (!transition.events.get(transition.index).equals(event)) {
                    throw new IllegalStateException("Source schema Events differ from the acknowledged transition");
                }
                Schema next = SchemaUtils.applySchemaChangeEvent(schemas.get(event.tableId()), event);
                transition.index++;
                if (transition.index == transition.events.size()) {
                    next = transition.after;
                    transitions.remove();
                }
                schemas.put(event.tableId(), next);
                return true;
            }
        }

        private Queue<SchemaAcknowledgement> remainingAcknowledgements(TableId table) {
            Queue<SchemaAcknowledgement> transitions = acknowledged.get(table);
            while (transitions != null
                    && !transitions.isEmpty()
                    && transitions.element().index == 0
                    && transitions.element().after.equals(schemas.get(table))) {
                transitions.remove();
            }
            return transitions;
        }

        private void requireAnnounced(TableId table) {
            if (!announced.contains(table))
                throw new IllegalStateException("Source Event precedes CREATE_TABLE metadata");
        }

        private void validateRow(RowData row, Schema schema) {
            if (row != null && row.getArity() != schema.columns().size()) {
                throw new IllegalArgumentException("Source row differs from its coordinated schema");
            }
        }

        private Event detach(Event event) {
            return event instanceof DataChangeEvent change
                    ? change.withImages(copyRow(change.before()), copyRow(change.after()))
                    : event;
        }

        private RowData copyRow(RowData row) {
            if (row == null) return null;
            Object[] fields = new Object[row.getArity()];
            for (int position = 0; position < fields.length; position++) fields[position] = row.getField(position);
            return GenericRowData.of(fields);
        }

        @Override
        public CompletableFuture<Void> isAvailable() {
            if (schemaResult != null) return schemaResult.thenApply(ignored -> null);
            return !pending.isEmpty() || sourceEnded ? CompletableFuture.completedFuture(null) : reader.isAvailable();
        }

        @Override
        public void addSplits(List<SplitT> splits) throws Exception {
            reader.addSplits(splits);
        }

        @Override
        public void notifyNoMoreSplits() {
            reader.notifyNoMoreSplits();
        }

        @Override
        public void handleSourceEvents(SourceEvent event) throws Exception {
            reader.handleSourceEvents(event);
        }

        @Override
        public List<SplitT> snapshotState(long checkpointId) throws Exception {
            if (!pending.isEmpty() || schemaResult != null) {
                throw new CheckpointDeclinedException(
                        checkpointId, "Source schema Events or following rows await delivery");
            }
            return reader.snapshotState(checkpointId);
        }

        @Override
        public void notifyCheckpointComplete(long checkpointId) throws Exception {
            reader.notifyCheckpointComplete(checkpointId);
        }

        @Override
        public void close() throws Exception {
            try {
                reader.close();
            } finally {
                pending.clear();
                synchronized (acknowledged) {
                    acknowledged.clear();
                }
            }
        }
    }

    private static final class SchemaAcknowledgement {
        private final TableId table;
        private final Schema after;
        private final List<SchemaChangeEvent> events;
        private int index;

        private SchemaAcknowledgement(TableId table, Schema before, Schema after) {
            this.table = table;
            this.after = after;
            events = before == null
                    ? List.of(new CreateTableEvent(table, after))
                    : SchemaUtils.diff(table, before, after);
        }
    }
}
