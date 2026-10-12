package io.yak.ops.flow.runtime.operators;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEventHandler;
import io.yak.ops.flow.runtime.source.event.AddSplitEvent;
import io.yak.ops.flow.runtime.source.event.NoMoreSplitsEvent;
import io.yak.ops.flow.runtime.source.event.SourceEventWrapper;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Task-owned operator that manages a SourceReader's execution and control events.
 *
 * <p>The owning mailbox thread creates, starts, polls and closes the reader. The Source
 * Coordinator manages split enumeration; the surrounding Task manages runtime context
 * and backpressure.
 *
 * @param <T> the record type emitted by the Source
 * @param <SplitT> the reader split type
 */
public final class SourceOperator<T, SplitT extends SourceSplit> implements OperatorEventHandler, AutoCloseable {

    private final Source<T, SplitT, ?> source;
    private final SourceReaderContext readerContext;
    private SourceReader<T, SplitT> reader;
    private boolean started;
    private boolean noMoreSplits;
    private boolean finished;

    public SourceOperator(Source<T, SplitT, ?> source, SourceReaderContext readerContext) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.readerContext = Objects.requireNonNull(readerContext, "readerContext must not be null");
    }

    /** Initializes the reader on the owning mailbox thread before coordinator registration. */
    public void initialize() throws Exception {
        if (reader != null) {
            throw new IllegalStateException("SourceOperator is already initialized");
        }
        reader = Objects.requireNonNull(source.createReader(readerContext), "Source returned a null reader");
    }

    /** Delivers restored split offsets before {@link SourceReader#start()} is called. */
    public void restoreSplits(List<SplitT> splits) throws Exception {
        ensureInitialized();
        if (started) {
            throw new IllegalStateException("Cannot restore splits directly into a started reader");
        }
        if (!splits.isEmpty()) {
            reader.addSplits(List.copyOf(splits));
        }
    }

    /** Starts the reader after the coordinator has registered its active attempt. */
    public void start() throws Exception {
        ensureInitialized();
        if (started) {
            throw new IllegalStateException("SourceReader is already started");
        }
        started = true;
        reader.start();
    }

    /**
     * Processes coordinator events serially with input polling on the mailbox thread.
     *
     * <p>Only split delivery and no-more-splits events are supported. The localized
     * type conversion avoids exposing erased split types to external callers.
     */
    @Override
    public void handleOperatorEvent(OperatorEvent event) throws Exception {
        Objects.requireNonNull(event, "event must not be null");
        if (event instanceof AddSplitEvent<?> splitsEvent) {
            handleAddSplits(splitsEvent);
        } else if (event instanceof NoMoreSplitsEvent noMoreEvent) {
            handleNoMoreSplits(noMoreEvent);
        } else if (event instanceof SourceEventWrapper wrapper) {
            ensureInitialized();
            reader.handleSourceEvents(wrapper.sourceEvent());
        } else {
            throw new IllegalArgumentException("Unsupported operator event for SourceOperator: "
                    + event.getClass().getName());
        }
    }

    /** Decodes and delivers split events on the owning task's mailbox thread. */
    @SuppressWarnings("unchecked")
    private void handleAddSplits(AddSplitEvent<?> event) throws Exception {
        ensureInitialized();
        Objects.requireNonNull(event, "event must not be null");
        if (noMoreSplits || finished) {
            throw new IllegalStateException("Cannot deliver splits after NoMoreSplits");
        }
        // Event carries serializer version + isolated bytes; no mutable SourceSplit crosses the gateway.
        reader.addSplits(((AddSplitEvent<SplitT>) event).splits(source.getSplitSerializer()));
    }

    /** Signals no further assignments without treating outstanding splits as finished. */
    private void handleNoMoreSplits(NoMoreSplitsEvent event) {
        ensureInitialized();
        Objects.requireNonNull(event, "event must not be null");
        if (noMoreSplits) {
            throw new IllegalStateException("NoMoreSplits was delivered more than once");
        }
        reader.notifyNoMoreSplits();
        noMoreSplits = true;
    }

    /** Polls the reader without blocking and emits its available records downstream. */
    public InputStatus emitNext(ReaderOutput<T> output) throws Exception {
        ensureStarted();
        if (finished) {
            return InputStatus.END_OF_INPUT;
        }
        InputStatus status = Objects.requireNonNull(reader.pollNext(output), "pollNext must not return null");
        if (status == InputStatus.END_OF_INPUT) {
            if (!noMoreSplits) {
                throw new IllegalStateException("Reader reported END_OF_INPUT before receiving NoMoreSplits");
            }
            finished = true;
        }
        return status;
    }

    /** Returns the reader's next availability future for task-mailbox suspension. */
    public CompletableFuture<Void> isAvailable() {
        ensureStarted();
        return Objects.requireNonNull(reader.isAvailable(), "isAvailable must not return null");
    }

    /** Returns only this reader's unfinished split progress, not a complete job checkpoint. */
    public List<SplitT> snapshotState(long checkpointId) throws Exception {
        ensureStarted();
        if (checkpointId < 0) {
            throw new IllegalArgumentException("checkpointId must not be negative");
        }
        return List.copyOf(
                Objects.requireNonNull(reader.snapshotState(checkpointId), "Reader snapshot must not be null"));
    }

    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        ensureStarted();
        reader.notifyCheckpointComplete(checkpointId);
    }

    @Override
    public void close() throws Exception {
        if (reader != null) {
            try {
                reader.close();
            } finally {
                reader = null;
            }
        }
    }

    private void ensureInitialized() {
        if (reader == null) {
            throw new IllegalStateException("SourceReader is not initialized");
        }
    }

    private void ensureStarted() {
        ensureInitialized();
        if (!started) {
            throw new IllegalStateException("SourceReader is not started");
        }
    }
}
