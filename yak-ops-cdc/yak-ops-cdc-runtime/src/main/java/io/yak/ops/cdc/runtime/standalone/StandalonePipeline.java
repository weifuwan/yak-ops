package io.yak.ops.cdc.runtime.standalone;

import io.yak.ops.cdc.runtime.api.sink.CommittingSinkWriter;
import io.yak.ops.cdc.runtime.api.sink.Sink;
import io.yak.ops.cdc.runtime.api.sink.SinkWriter;
import io.yak.ops.cdc.runtime.api.sink.SupportsCommitter;
import io.yak.ops.cdc.runtime.api.source.Source;
import io.yak.ops.cdc.runtime.api.source.SourceSplit;
import java.util.Objects;
import java.util.UUID;
import org.apache.flink.cdc.common.configuration.Configuration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.flink.cdc.common.event.Event;

/**
 * Local one-reader, one-writer CDC pipeline.
 *
 * <p>It executes the native Flink 1.20-style SourceReader / SplitEnumerator and Sink V2 contracts
 * with two data threads, one coordinator mailbox owned by the source thread, and a bounded
 * in-memory channel. No Flink StreamTask, JobManager, network shuffle or checkpoint is involved.
 *
 * <p>This first execution stage provides at-least-once-capable record transport only. It explicitly
 * rejects sinks requiring two-phase commit; it must not be used as a recoverable CDC runtime until
 * checkpoint coordination and durable state storage have been added.
 */
public final class StandalonePipeline<SplitT extends SourceSplit, EnumStateT> implements AutoCloseable {

    public enum State {
        CREATED,
        RUNNING,
        FINISHED,
        FAILED,
        CANCELLED
    }

    private final Source<Event, SplitT, EnumStateT> source;
    private final Sink<Event> sink;
    private final EventTransformer transform;
    private final Configuration configuration;
    private final BlockingQueue<EventEnvelope> records;
    private final CompletableFuture<State> completion = new CompletableFuture<>();
    private final AtomicReference<State> state = new AtomicReference<>(State.CREATED);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean sourceDone = new AtomicBoolean();
    private final AtomicInteger activeWorkers = new AtomicInteger(2);
    private final String jobId = UUID.randomUUID().toString();

    private volatile Thread sourceThread;
    private volatile Thread sinkThread;

    public StandalonePipeline(
            Source<Event, SplitT, EnumStateT> source,
            Sink<Event> sink,
            EventTransformer transform,
            int channelCapacity) {
        this(source, sink, transform, channelCapacity, new Configuration());
    }

    public StandalonePipeline(
            Source<Event, SplitT, EnumStateT> source,
            Sink<Event> sink,
            EventTransformer transform,
            int channelCapacity,
            Configuration configuration) {
        this.source = Objects.requireNonNull(source, "source");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.transform = Objects.requireNonNull(transform, "transform");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        if (channelCapacity <= 0) {
            throw new IllegalArgumentException("Channel capacity must be positive");
        }
        records = new ArrayBlockingQueue<>(channelCapacity);
    }

    /** Starts the local tasks once; the returned future completes after both tasks release resources. */
    public synchronized CompletableFuture<State> start() {
        if (!state.compareAndSet(State.CREATED, State.RUNNING)) {
            throw new IllegalStateException("Pipeline already started or terminated: " + state.get());
        }

        sourceThread = new Thread(this::runSource, "yak-cdc-source");
        sinkThread = new Thread(this::runSink, "yak-cdc-sink");
        sinkThread.start();
        sourceThread.start();
        return completion;
    }

    public State getState() {
        return state.get();
    }

    public Throwable getFailure() {
        return failure.get();
    }

    /** Cancellation interrupts blocked queue writers and readers and releases their resources. */
    public void cancel() {
        if (state.compareAndSet(State.CREATED, State.CANCELLED)) {
            cancelled.set(true);
            completion.complete(State.CANCELLED);
            return;
        }
        if (state.get() != State.RUNNING || !cancelled.compareAndSet(false, true)) {
            return;
        }
        interruptWorkers();
    }

    @Override
    public void close() {
        cancel();
    }

    private void runSource() {
        try {
            LocalSourceTask<SplitT, EnumStateT> task =
                    new LocalSourceTask<>(source, this::emitRecord, cancelled::get, configuration);
            task.execute();
        } catch (Throwable e) {
            if (!cancelled.get()) {
                reportFailure(e);
            }
        } finally {
            sourceDone.set(true);
            workerExited();
        }
    }

    private void emitRecord(EventEnvelope event) {
        try {
            records.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while handing off source event", e);
        }
    }

    private void runSink() {
        SinkWriter<Event> writer = null;
        try {
            if (sink instanceof SupportsCommitter<?>) {
                throw new UnsupportedOperationException(
                        "Two-phase committing sinks require checkpoint coordination");
            }
            writer = sink.createWriter(new LocalWriterContext(jobId, new LocalMetricGroup()));
            if (writer instanceof CommittingSinkWriter<?, ?>) {
                throw new UnsupportedOperationException(
                        "Committing SinkWriter requires checkpoint coordination");
            }
            while (!cancelled.get() && failure.get() == null) {
                EventEnvelope envelope = records.poll(80, TimeUnit.MILLISECONDS);
                if (envelope != null) {
                    Long timestamp = envelope.timestamp;
                    SinkWriter<Event> outputWriter = writer;
                    transform.process(
                            envelope.event,
                            output -> outputWriter.write(output, new LocalRecordContext(timestamp)));
                } else if (sourceDone.get()) {
                    break;
                }
            }
            if (!cancelled.get() && failure.get() == null) {
                writer.flush(true);
            }
        } catch (Throwable e) {
            if (!cancelled.get()) {
                reportFailure(e);
            }
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable e) {
                    if (!cancelled.get()) {
                        reportFailure(e);
                    }
                }
            }
            workerExited();
        }
    }

    private static final class LocalRecordContext implements SinkWriter.Context {
        private final Long timestamp;

        LocalRecordContext(Long timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public long currentWatermark() {
            return Long.MIN_VALUE;
        }

        @Override
        public Long timestamp() {
            return timestamp;
        }
    }

    private void reportFailure(Throwable error) {
        if (failure.compareAndSet(null, error)) {
            interruptWorkers();
        }
    }

    private void interruptWorkers() {
        Thread reader = sourceThread;
        Thread writer = sinkThread;
        if (reader != null) {
            reader.interrupt();
        }
        if (writer != null) {
            writer.interrupt();
        }
    }

    private void workerExited() {
        if (activeWorkers.decrementAndGet() != 0) {
            return;
        }
        Throwable error = failure.get();
        if (error != null) {
            state.set(State.FAILED);
            completion.completeExceptionally(error);
        } else if (cancelled.get()) {
            state.set(State.CANCELLED);
            completion.complete(State.CANCELLED);
        } else {
            state.set(State.FINISHED);
            completion.complete(State.FINISHED);
        }
    }
}
