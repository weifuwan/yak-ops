package io.yak.ops.connector.base.sink.writer;

import io.yak.ops.core.api.connector.sink.CancellableSinkWriter;
import io.yak.ops.core.api.connector.sink.ProcessingTimeService;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Mailbox-owned batch trigger and lifecycle for synchronous output implementations.
 *
 * <p>BatchOutput is the only owner of buffered records; this class retains no record queue.
 * Size, timed and checkpoint flushes are serialized by the task mailbox. Failed flushes do not
 * advance the Writer or silently retry uncertain external commits. Terminal cancellation is
 * signaled concurrently through BatchOutput.cancel(), while close() never flushes.
 *
 * @param <T> input record type
 */
public abstract class BatchingSinkWriterBase<T> implements CancellableSinkWriter<T> {

    private final BatchOutput<T> output;
    private final BatchFlushPolicy policy;
    private final ProcessingTimeService clock;
    private volatile ProcessingTimeService.TimerHandle timer;
    private volatile boolean cancelled;
    private volatile boolean closed;
    private boolean finished;
    private Throwable failure;

    protected BatchingSinkWriterBase(
            BatchOutput<T> output, BatchFlushPolicy policy, WriterInitContext context) {
        this.output = Objects.requireNonNull(output, "output");
        this.policy = Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(context, "context");
        this.clock = policy.hasTimedFlush() ? context.getProcessingTimeService() : null;
    }

    @Override
    public final void write(T element, SinkWriter.Context context) throws Exception {
        ensureWritable();
        Objects.requireNonNull(context, "context");
        try {
            output.add(Objects.requireNonNull(element, "element"));
        } catch (Exception | Error error) {
            failure = error;
            throw error;
        }
        if (output.bufferedRecords() >= policy.maxBatchSize()) {
            flush(false);
        } else if (output.bufferedRecords() > 0 && policy.hasTimedFlush() && timer == null) {
            scheduleTimer();
        }
    }

    @Override
    public final void flush(boolean endOfInput) throws Exception {
        ensureWritable();
        cancelTimer();
        if (output.bufferedRecords() > 0) {
            try {
                output.flush();
            } catch (Exception | Error error) {
                failure = error;
                throw error;
            }
        }
        if (endOfInput) {
            finished = true;
        }
    }

    private void scheduleTimer() {
        timer = clock.registerTimer(
                Math.addExact(clock.currentProcessingTime(), policy.flushInterval().toMillis()), this::onTimer);
    }

    private void onTimer() {
        timer = null;
        if (cancelled || closed || finished || output.bufferedRecords() == 0) {
            return;
        }
        try {
            flush(false);
        } catch (Exception error) {
            throw new IllegalStateException("Scheduled Sink batch flush failed", error);
        }
    }

    private void cancelTimer() {
        ProcessingTimeService.TimerHandle pending = timer;
        timer = null;
        if (pending != null) {
            pending.cancel();
        }
    }

    private void ensureWritable() {
        if (cancelled) {
            throw new CancellationException("Sink writer was cancelled");
        }
        if (closed || finished) {
            throw new IllegalStateException("Sink writer is closed or finished");
        }
        if (failure != null) {
            throw new IllegalStateException("Sink writer has a prior output failure", failure);
        }
    }

    @Override
    public final void cancel() {
        if (closed || cancelled) {
            return;
        }
        cancelled = true;
        cancelTimer();
        output.cancel();
    }

    @Override
    public final void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        cancelTimer();
        output.close();
    }
}
