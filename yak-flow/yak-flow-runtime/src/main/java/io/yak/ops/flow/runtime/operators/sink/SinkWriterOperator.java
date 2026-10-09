package io.yak.ops.flow.runtime.operators.sink;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.StatefulSinkWriter;
import io.yak.ops.core.api.connector.sink.SupportsWriterState;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.api.operators.Collector;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.flow.runtime.operators.CheckpointedStreamOperator;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import io.yak.ops.flow.runtime.state.OperatorStateBackend;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sink V2-style one-input operator. Stateful writer snapshots are versioned and restored
 * together with upstream operator/source progress by the aligned checkpoint coordinator.
 * No Committer or transactional exactly-once guarantee is implied.
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
    private SinkWriter<T> writer;
    private boolean opened;
    private boolean finished;
    private boolean closed;

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
        int stateCount = stateBackend.names().size();
        for (int index = 0; index < stateCount; index++) {
            restored.add(stateBackend
                    .get("writer-" + index, serializer)
                    .orElseThrow(() -> new IllegalStateException("Incomplete restored writer state")));
        }
        return Objects.requireNonNull(
                contract.restoreWriter(context, List.copyOf(restored)), "restoreWriter returned null");
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

    /** The Task mailbox invokes this at the aligned barrier, before acknowledgement. */
    @Override
    @SuppressWarnings("unchecked")
    public void snapshotState(long checkpointId, OperatorStateBackend backend) throws Exception {
        if (checkpointId <= 0) {
            throw new IllegalArgumentException("checkpointId must be positive");
        }
        requireOpen();
        writer.flush(false);
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

    @Override
    public void finish() throws Exception {
        requireOpen();
        writer.flush(true);
        finished = true;
    }

    private void requireOpen() {
        if (!opened || closed || finished || writer == null) {
            throw new IllegalStateException("SinkWriterOperator is not accepting records");
        }
    }

    @Override
    public void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
