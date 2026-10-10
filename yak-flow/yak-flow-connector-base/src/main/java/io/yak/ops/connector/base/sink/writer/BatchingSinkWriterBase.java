package io.yak.ops.connector.base.sink.writer;

import io.yak.ops.core.api.connector.sink.CancellableSinkWriter;
import io.yak.ops.core.api.connector.sink.ProcessingTimeService;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Implements mailbox-serialized batch triggers for a single-output Sink Writer.
 *
 * <p>{@link BatchOutput} is the only owner of buffered records; this base class holds no
 * second queue. Size and processing-time triggers call synchronous flush on the task mailbox
 * unless an output temporarily defers an incomplete logical update. Runtime checkpoint and
 * normal end-of-input flushes always execute and fail closed on incomplete updates.
 *
 * <p>An external I/O failure disables further writes without implicit retries. Terminal
 * cancellation may be signaled off-mailbox; close never flushes or commits records.
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

    /**
     * Binds an output and a flush policy to one task attempt's mailbox timer capability.
     *
     * <p>Timed policies require {@link WriterInitContext#getProcessingTimeService()}; an
     * unsupported timer capability fails immediately rather than silently disabling flush.
     *
     * @param output sole buffered-record owner for this Writer
     * @param policy positive batch threshold and optional flush interval
     * @param context task-local initialization metadata and mailbox timer service
     */
    protected BatchingSinkWriterBase(BatchOutput<T> output, BatchFlushPolicy policy, WriterInitContext context) {
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
        int pending = output.bufferedRecords();
        if (pending >= policy.maxBatchSize() && output.canAutomaticallyFlush()) {
            flush(false);
        } else if (pending > 0 && policy.hasTimedFlush() && timer == null && output.canAutomaticallyFlush()) {
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
                Math.addExact(
                        clock.currentProcessingTime(), policy.flushInterval().toMillis()),
                this::onTimer);
    }

    private void onTimer() {
        timer = null;
        if (cancelled || closed || finished || output.bufferedRecords() == 0 || !output.canAutomaticallyFlush()) {
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
