package io.yak.ops.cdc.runtime.standalone;

import io.yak.ops.cdc.runtime.api.source.InputStatus;
import io.yak.ops.cdc.runtime.api.source.ReaderInfo;
import io.yak.ops.cdc.runtime.api.source.ReaderOutput;
import io.yak.ops.cdc.runtime.api.source.Source;
import io.yak.ops.cdc.runtime.api.source.SourceEvent;
import io.yak.ops.cdc.runtime.api.source.SourceOutput;
import io.yak.ops.cdc.runtime.api.source.SourceReader;
import io.yak.ops.cdc.runtime.api.source.SourceReaderContext;
import io.yak.ops.cdc.runtime.api.source.SourceSplit;
import io.yak.ops.cdc.runtime.api.source.SplitEnumerator;
import io.yak.ops.cdc.runtime.api.source.SplitEnumeratorContext;
import io.yak.ops.cdc.runtime.api.source.SplitsAssignment;
import io.yak.ops.cdc.runtime.api.source.Watermark;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import org.apache.flink.cdc.common.configuration.Configuration;
import org.apache.flink.cdc.common.event.Event;

/**
 * Owns the single source reader and enumerator for one local pipeline.
 *
 * <p>Enumerator callbacks, split assignments and reader polls are serialized on the source task
 * thread. Blocking split discovery runs on a separate executor; its completion is handed back to
 * this thread. A future parallel-reader implementation must preserve that ownership rule.
 */
final class LocalSourceTask<SplitT extends SourceSplit, EnumStateT> implements AutoCloseable {

    private static final int READER_ID = 0;

    private final Source<Event, SplitT, EnumStateT> source;
    private final java.util.function.Consumer<EventEnvelope> collector;
    private final BooleanSupplier cancelled;
    private final Configuration configuration;
    private final BlockingQueue<Runnable> coordinatorActions = new LinkedBlockingQueue<>();
    private final LocalMetricGroup metrics = new LocalMetricGroup();
    private final ExecutorService asyncExecutor =
            Executors.newCachedThreadPool(task -> newDaemonThread(task, "yak-cdc-source-async"));
    private final ScheduledExecutorService scheduledExecutor =
            Executors.newSingleThreadScheduledExecutor(task -> newDaemonThread(task, "yak-cdc-source-timer"));

    private static Thread newDaemonThread(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private SourceReader<Event, SplitT> reader;
    private SplitEnumerator<SplitT, EnumStateT> enumerator;

    LocalSourceTask(
            Source<Event, SplitT, EnumStateT> source,
            java.util.function.Consumer<EventEnvelope> collector,
            BooleanSupplier cancelled,
            Configuration configuration) {
        this.source = Objects.requireNonNull(source, "source");
        this.collector = Objects.requireNonNull(collector, "collector");
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    void execute() throws Exception {
        try {
            reader = source.createReader(new LocalReaderContext());
            enumerator = source.createEnumerator(new LocalEnumeratorContext());
            reader.start();
            enumerator.start();
            enumerator.addReader(READER_ID);

            ReaderOutput<Event> output = new LocalReaderOutput();
            boolean waitingForData = false;
            while (!cancelled.getAsBoolean()) {
                Runnable action;
                while ((action = coordinatorActions.poll()) != null) {
                    action.run();
                }

                if (waitingForData) {
                    CompletableFuture<Void> future = reader.isAvailable();
                    if (!future.isDone()) {
                        action = coordinatorActions.poll(40, TimeUnit.MILLISECONDS);
                        if (action != null) {
                            action.run();
                        }
                        continue;
                    }
                    future.join();
                }

                InputStatus status = reader.pollNext(output);
                if (status == InputStatus.END_OF_INPUT) {
                    return;
                }
                waitingForData = status == InputStatus.NOTHING_AVAILABLE;
            }
        } finally {
            close();
        }
    }

    private void enqueue(Runnable action) {
        if (!cancelled.getAsBoolean()) {
            coordinatorActions.add(action);
        }
    }

    private void validateSubtask(int subtask) {
        if (subtask != READER_ID) {
            throw new IllegalArgumentException("Standalone source supports reader 0 only: " + subtask);
        }
    }

    @Override
    public void close() throws Exception {
        scheduledExecutor.shutdownNow();
        asyncExecutor.shutdownNow();
        Exception error = null;
        if (enumerator != null) {
            try {
                enumerator.close();
            } catch (Exception e) {
                error = e;
            }
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception e) {
                if (error == null) {
                    error = e;
                } else {
                    error.addSuppressed(e);
                }
            }
        }
        if (error != null) {
            throw error;
        }
    }

    private final class LocalReaderContext implements SourceReaderContext {

        @Override
        public LocalMetricGroup metricGroup() {
            return metrics;
        }

        @Override
        public Configuration getConfiguration() {
            return configuration;
        }

        @Override
        public String getLocalHostName() {
            return "localhost";
        }

        @Override
        public int getIndexOfSubtask() {
            return READER_ID;
        }

        @Override
        public void sendSplitRequest() {
            enqueue(() -> enumerator.handleSplitRequest(READER_ID, getLocalHostName()));
        }

        @Override
        public void sendSourceEventToCoordinator(SourceEvent event) {
            enqueue(() -> enumerator.handleSourceEvent(READER_ID, event));
        }

        @Override
        public ClassLoader getUserCodeClassLoader() {
            return Thread.currentThread().getContextClassLoader();
        }

        @Override
        public int currentParallelism() {
            return 1;
        }
    }

    private final class LocalEnumeratorContext implements SplitEnumeratorContext<SplitT> {

        @Override
        public LocalMetricGroup metricGroup() {
            return metrics;
        }

        @Override
        public void sendEventToSourceReader(int subtaskId, SourceEvent event) {
            validateSubtask(subtaskId);
            reader.handleSourceEvents(event);
        }

        @Override
        public int currentParallelism() {
            return 1;
        }

        @Override
        public Map<Integer, ReaderInfo> registeredReaders() {
            return Map.of(READER_ID, new ReaderInfo(READER_ID, "localhost"));
        }

        @Override
        public void assignSplits(SplitsAssignment<SplitT> assignments) {
            for (Map.Entry<Integer, List<SplitT>> entry : assignments.assignment().entrySet()) {
                validateSubtask(entry.getKey());
                reader.addSplits(List.copyOf(entry.getValue()));
            }
        }

        @Override
        public void signalNoMoreSplits(int subtaskId) {
            validateSubtask(subtaskId);
            reader.notifyNoMoreSplits();
        }

        @Override
        public <T> void callAsync(Callable<T> callable, BiConsumer<T, Throwable> handler) {
            asyncExecutor.execute(() -> {
                T result = null;
                Throwable error = null;
                try {
                    result = callable.call();
                } catch (Throwable e) {
                    error = e;
                }
                T completed = result;
                Throwable failure = error;
                enqueue(() -> handler.accept(completed, failure));
            });
        }

        @Override
        public <T> void callAsync(
                Callable<T> callable,
                BiConsumer<T, Throwable> handler,
                long initialDelayMillis,
                long periodMillis) {
            if (initialDelayMillis < 0 || periodMillis <= 0) {
                throw new IllegalArgumentException("Invalid split discovery interval");
            }
            scheduledExecutor.scheduleAtFixedRate(
                    () -> callAsync(callable, handler),
                    initialDelayMillis,
                    periodMillis,
                    TimeUnit.MILLISECONDS);
        }

        @Override
        public void runInCoordinatorThread(Runnable runnable) {
            enqueue(runnable);
        }

        @Override
        public void setIsProcessingBacklog(boolean backlog) {
            metrics.gauge("sourceProcessingBacklog", () -> backlog);
        }
    }

    private final class LocalReaderOutput implements ReaderOutput<Event> {

        @Override
        public void collect(Event record) {
            collector.accept(new EventEnvelope(record, null));
        }

        @Override
        public void collect(Event record, long timestamp) {
            collector.accept(new EventEnvelope(record, timestamp));
        }

        @Override
        public SourceOutput<Event> createOutputForSplit(String splitId) {
            return this;
        }

        @Override
        public void releaseOutputForSplit(String splitId) {
            // Event-time watermark alignment is not part of this CDC execution backend.
        }

        @Override
        public void emitWatermark(Watermark watermark) {
            throw new UnsupportedOperationException("Event-time watermarks are not supported");
        }

        @Override
        public void markIdle() {
            // No event-time watermark coordination in this runtime.
        }
    }
}
