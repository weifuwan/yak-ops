package io.yak.ops.flow.runtime.operators.sink;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.StatefulSinkWriter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.api.operators.Collector;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import java.util.Objects;

/**
 * Sink V2-style one-input operator. Owns the writer lifecycle in both chained and unchained tasks.
 * Does not commit transactions or pretend that writer state is persisted by the current checkpoint.
 */
public final class SinkWriterOperator<T> implements OneInputStreamOperator<T, Void> {

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
    private SinkWriter<T> writer;
    private boolean opened;
    private boolean finished;
    private boolean closed;

    public SinkWriterOperator(Sink<T> sink, WriterInitContext context) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public void open() throws Exception {
        if (opened || closed) {
            throw new IllegalStateException("SinkWriterOperator cannot be opened twice");
        }
        opened = true;
        writer = Objects.requireNonNull(sink.createWriter(context), "Sink returned a null SinkWriter");
        if (writer instanceof StatefulSinkWriter<?, ?> && (!context.getConfiguration()
                .get(CheckpointingOptions.CHECKPOINTING_INTERVAL).isZero()
                || context.getConfiguration().get(CheckpointingOptions.RESTORE_LATEST))) {
            try {
                writer.close();
            } finally {
                writer = null;
            }
            throw new UnsupportedOperationException(
                    "Stateful SinkWriter requires persisted writer state; current checkpoint only supports stateless sinks");
        }
    }

    @Override
    public void processElement(T element, Collector<Void> output) throws Exception {
        requireOpen();
        Objects.requireNonNull(output, "output");
        writer.write(element, RECORD_CONTEXT);
    }

    /** Called by the existing quiescent coordinator after InputGates have drained. */
    public void flushForCheckpoint(long checkpointId) throws Exception {
        if (checkpointId <= 0) {
            throw new IllegalArgumentException("checkpointId must be positive");
        }
        requireOpen();
        writer.flush(false);
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
