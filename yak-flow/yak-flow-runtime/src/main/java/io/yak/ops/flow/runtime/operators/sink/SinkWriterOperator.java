package io.yak.ops.flow.runtime.operators.sink;

import io.yak.ops.core.api.connector.sink.CancellableSinkWriter;
import io.yak.ops.core.api.connector.sink.Committer;
import io.yak.ops.core.api.connector.sink.CommittingSinkWriter;
import io.yak.ops.core.api.connector.sink.SchemaChangeAwareSinkWriter;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.StatefulSinkWriter;
import io.yak.ops.core.api.connector.sink.SupportsCommitter;
import io.yak.ops.core.api.connector.sink.SupportsWriterState;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.api.operators.Collector;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.flow.runtime.operators.CheckpointedStreamOperator;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import io.yak.ops.flow.runtime.state.OperatorStateBackend;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Owns the Sink Writer lifecycle for chained and standalone one-input StreamTasks.
 *
 * <p>The task mailbox serializes record writes, explicit checkpoint flushes and normal
 * end-of-input completion. Stateful Writer snapshots are versioned and restored through
 * the aligned Checkpoint coordinator; stateless JDBC writers rely on upstream replay for
 * at-least-once delivery. Terminal I/O cancellation can be signaled off-mailbox, while
 * Writer cleanup remains task-owned.
 *
 * <p>Checkpoint ACK is not a two-phase commit, and no XA/Committer or exactly-once
 * guarantee is implied.
 */
public final class SinkWriterOperator<T> implements OneInputStreamOperator<T, Void>, CheckpointedStreamOperator {

    private static final SinkWriter.Context RECORD_CONTEXT = new SinkWriter.Context() {
        @Override
        public Long timestamp() {
            return null;
        }

        @Override
        public long currentWatermark() {
            return Long.MIN_VALUE;
        }
    };

    private final Sink<T> sink;
    private final WriterInitContext context;
    private OperatorStateBackend stateBackend;
    private volatile SinkWriter<T> writer;
    private Committer<Object> committer;
    private long pendingCommitCheckpointId = -1;
    private boolean opened;
    private boolean finished;
    private boolean closed;

    /**
     * Binds a reusable Sink definition to the actual Task's writer initialization context.
     *
     * <p>Writer creation remains deferred until {@link #open()}, after any state backend
     * has been initialized or restored.
     *
     * @param sink reusable task-independent Sink definition
     * @param context task identity, effective configuration and mailbox timers
     */
    public SinkWriterOperator(Sink<T> sink, WriterInitContext context) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public void initializeState(OperatorStateBackend backend) {
        if (opened || stateBackend != null) {
            throw new IllegalStateException("SinkWriterOperator already initialized");
        }
        stateBackend = Objects.requireNonNull(backend, "backend");
    }

    @Override
    public void open() throws Exception {
        if (opened || closed) {
            throw new IllegalStateException("SinkWriterOperator cannot be opened twice");
        }
        opened = true;
        boolean checkpointEnabled = !context.getConfiguration()
                        .get(CheckpointingOptions.CHECKPOINTING_INTERVAL)
                        .isZero()
                || context.getConfiguration().get(CheckpointingOptions.RESTORE_LATEST);
        if (sink instanceof SupportsCommitter<?, ?> transactional) {
            if (!checkpointEnabled || stateBackend == null) {
                throw new UnsupportedOperationException("Committing Sink requires durable checkpoint state");
            }
            openCommitter(transactional);
        } else if (stateBackend != null
                && stateBackend.names().stream().anyMatch(name -> name.startsWith("operator/committable-"))) {
            throw new IllegalStateException("Restored committables require the original committing Sink");
        }
        if (sink instanceof SupportsWriterState<?, ?> statefulSink) {
            if (stateBackend == null) {
                // Direct SinkWriterOperator callers may not have an enclosing physical StreamTask.
                throw new IllegalStateException("Stateful Sink requires an initialized operator state backend");
            }
            writer = restoreWriter(statefulSink);
        } else {
            if (stateBackend != null && !stateBackend.snapshot().isEmpty()) {
                throw new IllegalStateException("Sink has persisted state but no restore contract");
            }
            writer = Objects.requireNonNull(sink.createWriter(context), "Sink returned a null SinkWriter");
        }
        if (checkpointEnabled
                && writer instanceof StatefulSinkWriter<?, ?>
                && !(sink instanceof SupportsWriterState<?, ?>)) {
            rejectUnsafeWriter("Stateful SinkWriter requires SupportsWriterState and a state serializer");
        }
        if (checkpointEnabled
                && sink instanceof SupportsWriterState<?, ?>
                && !(writer instanceof StatefulSinkWriter<?, ?>)) {
            rejectUnsafeWriter("restoreWriter must return a StatefulSinkWriter");
        }
    }

    @SuppressWarnings("unchecked")
    private SinkWriter<T> restoreWriter(SupportsWriterState<?, ?> raw) throws Exception {
        SupportsWriterState<T, Object> contract = (SupportsWriterState<T, Object>) raw;
        SimpleVersionedSerializer<Object> serializer =
                Objects.requireNonNull(contract.getWriterStateSerializer(), "Writer state serializer");
        List<Object> restored = new ArrayList<>();
        // Numbered writer state must be dense; no silent partial restore of a corrupted snapshot.
        int stateCount = (int) stateBackend.names().stream()
                .filter(name -> name.startsWith("operator/writer-"))
                .count();
        for (int index = 0; index < stateCount; index++) {
            restored.add(stateBackend
                    .get("writer-" + index, serializer)
                    .orElseThrow(() -> new IllegalStateException("Incomplete restored writer state")));
        }
        return Objects.requireNonNull(
                contract.restoreWriter(context, List.copyOf(restored)), "restoreWriter returned null");
    }

    @SuppressWarnings("unchecked")
    private void openCommitter(SupportsCommitter<?, ?> raw) throws Exception {
        SupportsCommitter<T, Object> contract = (SupportsCommitter<T, Object>) raw;
        var serializer = Objects.requireNonNull(contract.getCommittableSerializer(), "Committable serializer");
        committer = Objects.requireNonNull(contract.createCommitter(context), "Sink committer");
        var restored = readCommittables(serializer);
        committer.recover(restored);
        if (!restored.isEmpty()) {
            // Only durable checkpoint committables reach the restored writer.
            committer.commit(restored, true);
            stateBackend.removeByPrefix("committable-");
        }
    }

    private java.util.List<Object> readCommittables(SimpleVersionedSerializer<Object> serializer) throws Exception {
        int count = (int) stateBackend.names().stream()
                .filter(name -> name.startsWith("operator/committable-"))
                .count();
        var values = new java.util.ArrayList<Object>(count);
        for (int index = 0; index < count; index++) {
            values.add(stateBackend
                    .get("committable-" + index, serializer)
                    .orElseThrow(() -> new IllegalStateException("Incomplete restored committable state")));
        }
        return java.util.List.copyOf(values);
    }

    private void rejectUnsafeWriter(String reason) throws Exception {
        try {
            writer.close();
        } finally {
            writer = null;
        }
        throw new UnsupportedOperationException(reason);
    }

    @Override
    public void processElement(T element, Collector<Void> output) throws Exception {
        requireOpen();
        Objects.requireNonNull(output, "output");
        writer.write(element, RECORD_CONTEXT);
    }

    /**
     * Flushes the Writer on the mailbox before completing the aligned checkpoint snapshot.
     *
     * <p>Incomplete Connector input must fail here, not be acknowledged. For stateful
     * Writers, the versioned state is stored under the operator's writer namespace;
     * stateless Writers only flush their external output.
     *
     * @param checkpointId positive aligned-checkpoint identity
     * @param backend operator-scoped state snapshot destination
     * @throws Exception if flush, serialization or snapshot construction fails
     */
    @Override
    @SuppressWarnings("unchecked")
    public void snapshotState(long checkpointId, OperatorStateBackend backend) throws Exception {
        if (checkpointId <= 0) {
            throw new IllegalArgumentException("checkpointId must be positive");
        }
        requireOpen();
        writer.flush(false);
        if (committer != null) {
            if (!(writer instanceof CommittingSinkWriter<?, ?> committingWriter)) {
                throw new IllegalStateException("Committing Sink requires a CommittingSinkWriter");
            }
            SupportsCommitter<T, Object> contract = (SupportsCommitter<T, Object>) sink;
            java.util.Collection<Object> prepared =
                    ((CommittingSinkWriter<T, Object>) committingWriter).prepareCommit(checkpointId);
            Objects.requireNonNull(prepared, "Prepared committables");
            // A declined checkpoint does not rewind the running Source. Its prepared
            // transactions must survive into the next checkpoint, or rows would be lost.
            int index = readCommittables(contract.getCommittableSerializer()).size();
            if (index + prepared.size() > 1_000) {
                throw new IllegalStateException("Too many pending XA transactions after declined checkpoints");
            }
            for (Object committable : prepared) {
                stateBackend.put(
                        "committable-" + index++,
                        Objects.requireNonNull(committable),
                        contract.getCommittableSerializer());
            }
            pendingCommitCheckpointId = checkpointId;
        }
        if (!(writer instanceof StatefulSinkWriter<?, ?> statefulWriter)) {
            return;
        }
        if (!(sink instanceof SupportsWriterState<?, ?> statefulSink)) {
            throw new UnsupportedOperationException("Stateful writer cannot snapshot without restore contract");
        }
        SupportsWriterState<T, Object> contract = (SupportsWriterState<T, Object>) statefulSink;
        SimpleVersionedSerializer<Object> serializer =
                Objects.requireNonNull(contract.getWriterStateSerializer(), "Writer state serializer");
        List<Object> states = (List<Object>) statefulWriter.snapshotState(checkpointId);
        Objects.requireNonNull(states, "Writer snapshot state");
        backend.removeByPrefix("writer-");
        for (int i = 0; i < states.size(); i++) {
            backend.put("writer-" + i, states.get(i), serializer);
        }
    }

    /**
     * Commits only after global checkpoint durability. Disk state retains prepared XIDs
     * after commit so a crash between database acknowledgment and next checkpoint is recoverable.
     */
    @SuppressWarnings("unchecked")
    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        if (committer == null || pendingCommitCheckpointId != checkpointId) return;
        SupportsCommitter<T, Object> contract = (SupportsCommitter<T, Object>) sink;
        committer.commit(readCommittables(contract.getCommittableSerializer()), false);
        stateBackend.removeByPrefix("committable-");
        pendingCommitCheckpointId = -1;
    }

    /**
     * Performs the only normal end-of-input flush; failure/cancellation never invokes it.
     *
     * @throws Exception if completing the Writer's pending output fails
     */
    @Override
    public void finish() throws Exception {
        requireOpen();
        // A failed final flush can have committed externally. Never attempt it again in this Writer.
        finished = true;
        writer.flush(true);
    }

    /** Validates the Writer's schema-adoption capability before any target DDL is executed. */
    @SuppressWarnings("unchecked")
    public void validateSchemaChange(SchemaChangeRequest request) throws Exception {
        requireOpen();
        if (!(writer instanceof SchemaChangeAwareSinkWriter<?> aware)) {
            throw new UnsupportedOperationException("Sink Writer does not support runtime schema changes");
        }
        ((SchemaChangeAwareSinkWriter<T>) aware).validateSchemaChange(request);
    }

    /** Flushes every buffered row on the Writer's owning mailbox before target DDL. */
    public void prepareSchemaChange(SchemaChangeRequest request) throws Exception {
        validateSchemaChange(request);
        writer.flush(false);
    }

    /** Rebuilds the immutable write plan only after the MetadataApplier completed the target change. */
    @SuppressWarnings("unchecked")
    public void commitSchemaChange(SchemaChangeRequest request, long schemaVersion) throws Exception {
        requireOpen();
        if (!(writer instanceof SchemaChangeAwareSinkWriter<?> aware)) {
            throw new UnsupportedOperationException("Sink Writer does not support runtime schema changes");
        }
        ((SchemaChangeAwareSinkWriter<T>) aware).onSchemaChange(request, schemaVersion);
    }

    private void requireOpen() {
        if (!opened || closed || finished || writer == null) {
            throw new IllegalStateException("SinkWriterOperator is not accepting records");
        }
    }

    /**
     * Sends an optional terminal I/O cancellation signal from outside the mailbox thread.
     *
     * <p>The Writer must return immediately and must not commit, flush, or close in this call.
     */
    public void requestCancel() {
        SinkWriter<T> active = writer;
        if (active instanceof CancellableSinkWriter<?> cancellable) {
            cancellable.cancel();
        }
    }

    @Override
    public void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        Exception failure = null;
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception error) {
                failure = error;
            }
            writer = null;
        }
        if (committer != null) {
            try {
                committer.close();
            } catch (Exception error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
            committer = null;
        }
        if (failure != null) throw failure;
    }
}
