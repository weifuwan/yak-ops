package io.yak.ops.connector.base.source;

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
import io.yak.ops.core.data.ChangeEventId;
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
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Converts a connector's indivisible table mutations at its reader output boundary.
 *
 * <p>Enumerators, source splits and their versioned serializers remain owned by the connector.
 * An UPDATE pair must be emitted within one poll, so no buffered image can cross a checkpoint.
 */
public final class TableEventSource<SplitT extends SourceSplit, StateT> implements Source<Event, SplitT, StateT> {
    private final Source<TableRecord, SplitT, StateT> source;
    private Map<TableId, Schema> schemas;

    public TableEventSource(Source<TableRecord, SplitT, StateT> source, Map<TableId, Schema> schemas) {
        this.source = Objects.requireNonNull(source, "source");
        this.schemas = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(schemas, "schemas")));
    }

    @Override
    public void restoreDiscoveredTables(Map<TableId, Schema> restored) {
        source.restoreDiscoveredTables(restored);
        schemas = Map.copyOf(restored);
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
    public SplitEnumerator<SplitT, StateT> restoreEnumerator(
            SplitEnumeratorContext<SplitT> context, StateT checkpointState) throws Exception {
        return source.restoreEnumerator(context, checkpointState);
    }

    @Override
    public SourceReader<Event, SplitT> createReader(SourceReaderContext context) throws Exception {
        return new EventReader(context);
    }

    @Override
    public SimpleVersionedSerializer<SplitT> getSplitSerializer() {
        return source.getSplitSerializer();
    }

    @Override
    public SimpleVersionedSerializer<StateT> getEnumeratorCheckpointSerializer() {
        return source.getEnumeratorCheckpointSerializer();
    }

    private final class EventReader implements SourceReader<Event, SplitT> {
        private final SourceReader<TableRecord, SplitT> reader;
        private final Queue<Event> metadata = new ArrayDeque<>();
        private final Map<TableId, Schema> announcedSchemas = new LinkedHashMap<>();
        private CompletableFuture<Void> metadataAvailable = new CompletableFuture<>();
        private TableRecord pendingBefore;

        private EventReader(SourceReaderContext context) throws Exception {
            for (Map.Entry<TableId, Schema> entry : schemas.entrySet()) {
                Schema restored = context.restoredSourceSchema(entry.getKey()).orElse(entry.getValue());
                announcedSchemas.put(entry.getKey(), restored);
                metadata.add(new CreateTableEvent(entry.getKey(), restored));
            }
            if (!metadata.isEmpty()) {
                metadataAvailable.complete(null);
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
                public CompletionStage<Long> sendSchemaChangeRequestToCoordinator(SchemaChangeRequest request) {
                    return context.sendSchemaChangeRequestToCoordinator(request);
                }

                @Override
                public CompletionStage<Long> sendSourceSchemaChange(SourceSchemaChange change) {
                    final List<SchemaChangeEvent> events;
                    try {
                        synchronized (metadata) {
                            Schema announced = announcedSchemas.get(change.tableId());
                            if (announced == null && !change.newlyAdded()
                                    || announced != null
                                            && !announced.equals(change.before())
                                            && !announced.equals(change.after())) {
                                throw new IllegalStateException(
                                        "Source schema history does not match the emitted metadata");
                            }
                            events = announced == null
                                    ? List.of(new CreateTableEvent(change.tableId(), change.after()))
                                    : SchemaUtils.diff(change.tableId(), announced, change.after());
                        }
                    } catch (RuntimeException failure) {
                        return CompletableFuture.failedFuture(failure);
                    }
                    return context.sendSourceSchemaChange(change).thenApply(version -> {
                        synchronized (metadata) {
                            announcedSchemas.put(change.tableId(), change.after());
                            metadata.addAll(events);
                            if (!events.isEmpty()) {
                                metadataAvailable.complete(null);
                            }
                        }
                        return version;
                    });
                }

                @Override
                public Optional<Schema> restoredSourceSchema(TableId table) {
                    return context.restoredSourceSchema(table);
                }

                @Override
                public long sourceSchemaVersion(TableId table) {
                    return context.sourceSchemaVersion(table);
                }
            });
        }

        @Override
        public void start() throws Exception {
            reader.start();
        }

        @Override
        public InputStatus pollNext(ReaderOutput<Event> output) throws Exception {
            emitMetadata(output);
            InputStatus status = reader.pollNext(row -> emit(row, output));
            if (pendingBefore != null) {
                throw new IllegalStateException("Source split an UPDATE across mailbox emissions");
            }
            synchronized (metadata) {
                return metadata.isEmpty() ? status : InputStatus.MORE_AVAILABLE;
            }
        }

        private void emitMetadata(ReaderOutput<Event> output) throws Exception {
            for (; ; ) {
                Event event;
                synchronized (metadata) {
                    event = metadata.poll();
                    if (event == null) {
                        if (metadataAvailable.isDone()) {
                            metadataAvailable = new CompletableFuture<>();
                        }
                        return;
                    }
                }
                output.collect(event);
            }
        }

        private void emit(TableRecord row, ReaderOutput<Event> output) throws Exception {
            emitMetadata(output);
            if (pendingBefore != null) {
                TableRecord before = pendingBefore;
                if (row.rowKind() != RowKind.UPDATE_AFTER || !row.tableId().equals(before.tableId())) {
                    throw new IllegalStateException("Source emitted a non-adjacent UPDATE pair");
                }
                ChangeEventId first = before.changeEventId();
                ChangeEventId second = row.changeEventId();
                if (first == null
                        ? second != null
                        : !first.sameEvent(second)
                                || first.imageIndex() != 0
                                || first.imageCount() != 2
                                || second.imageIndex() != 1
                                || second.imageCount() != 2) {
                    throw new IllegalStateException("Source UPDATE images do not share an indivisible change identity");
                }
                DataChangeEvent change = DataChangeEvent.updateEvent(row.tableId(), before.row(), row.row());
                output.collect(change.withChangeEventId(first));
                pendingBefore = null;
                return;
            }
            switch (row.rowKind()) {
                case INSERT ->
                    output.collect(DataChangeEvent.insertEvent(row.tableId(), row.row())
                            .withChangeEventId(row.changeEventId()));
                case DELETE ->
                    output.collect(DataChangeEvent.deleteEvent(row.tableId(), row.row())
                            .withChangeEventId(row.changeEventId()));
                case UPDATE_BEFORE -> pendingBefore = row;
                case UPDATE_AFTER -> throw new IllegalStateException("Source UPDATE_AFTER has no before image");
            }
        }

        @Override
        public CompletableFuture<Void> isAvailable() {
            synchronized (metadata) {
                return reader.isAvailable().applyToEither(metadataAvailable, value -> null);
            }
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
            if (pendingBefore != null) {
                throw new IllegalStateException("Cannot snapshot an incomplete UPDATE");
            }
            return reader.snapshotState(checkpointId);
        }

        @Override
        public void notifyCheckpointComplete(long checkpointId) throws Exception {
            reader.notifyCheckpointComplete(checkpointId);
        }

        @Override
        public void close() throws Exception {
            reader.close();
        }
    }
}
