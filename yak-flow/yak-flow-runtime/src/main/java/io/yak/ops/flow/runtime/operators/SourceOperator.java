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
 * Task-owned wrapper around a SourceReader, following Flink's SourceOperator responsibilities.
 *
 * <p>The owning mailbox thread creates, starts, polls and closes the reader; this class does
 * not own the SplitEnumerator or a second reader thread. Runtime context and backpressure
 * remain owned by the surrounding task.
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
        this.source = Objects.requireNonNull(source, "source 不能为空");
        this.readerContext = Objects.requireNonNull(readerContext, "readerContext 不能为空");
    }

    /** Initializes the reader on the owning mailbox thread before coordinator registration. */
    public void initialize() throws Exception {
        if (reader != null) {
            throw new IllegalStateException("SourceOperator 已初始化");
        }
        reader = Objects.requireNonNull(source.createReader(readerContext), "Source 返回空 Reader");
    }

    /** Delivers restored split offsets before {@link SourceReader#start()} is called. */
    public void restoreSplits(List<SplitT> splits) throws Exception {
        ensureInitialized();
        if (started) {
            throw new IllegalStateException("已经启动的 Reader 不允许直接恢复 Split");
        }
        if (!splits.isEmpty()) {
            reader.addSplits(List.copyOf(splits));
        }
    }

    /** Starts the reader after the coordinator has registered its active attempt. */
    public void start() throws Exception {
        ensureInitialized();
        if (started) {
            throw new IllegalStateException("SourceReader 已启动");
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
        Objects.requireNonNull(event, "event 不能为空");
        if (event instanceof AddSplitEvent<?> splitsEvent) {
            handleAddSplits(splitsEvent);
        } else if (event instanceof NoMoreSplitsEvent noMoreEvent) {
            handleNoMoreSplits(noMoreEvent);
        } else if (event instanceof SourceEventWrapper wrapper) {
            ensureInitialized();
            reader.handleSourceEvents(wrapper.sourceEvent());
        } else {
            throw new IllegalArgumentException(
                    "SourceOperator 不支持的 OperatorEvent：" + event.getClass().getName());
        }
    }

    /** Decodes and delivers split events on the owning task's mailbox thread. */
    @SuppressWarnings("unchecked")
    private void handleAddSplits(AddSplitEvent<?> event) throws Exception {
        ensureInitialized();
        Objects.requireNonNull(event, "event 不能为空");
        if (noMoreSplits || finished) {
            throw new IllegalStateException("NoMoreSplits 后不能再交付新 Split");
        }
        // Event carries serializer version + isolated bytes; no mutable SourceSplit crosses the gateway.
        reader.addSplits(((AddSplitEvent<SplitT>) event).splits(source.getSplitSerializer()));
    }

    /** Signals no further assignments without treating outstanding splits as finished. */
    private void handleNoMoreSplits(NoMoreSplitsEvent event) {
        ensureInitialized();
        Objects.requireNonNull(event, "event 不能为空");
        if (noMoreSplits) {
            throw new IllegalStateException("NoMoreSplits 重复交付");
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
        InputStatus status = Objects.requireNonNull(reader.pollNext(output), "pollNext 不能返回 null");
        if (status == InputStatus.END_OF_INPUT) {
            if (!noMoreSplits) {
                throw new IllegalStateException("Reader 未收到 NoMoreSplits 却报告 END_OF_INPUT");
            }
            finished = true;
        }
        return status;
    }

    /** Returns the reader's next availability future for task-mailbox suspension. */
    public CompletableFuture<Void> isAvailable() {
        ensureStarted();
        return Objects.requireNonNull(reader.isAvailable(), "isAvailable 不能返回 null");
    }

    /** Returns only this reader's unfinished split progress, not a complete job checkpoint. */
    public List<SplitT> snapshotState(long checkpointId) throws Exception {
        ensureStarted();
        if (checkpointId < 0) {
            throw new IllegalArgumentException("checkpointId 不能为负数");
        }
        return List.copyOf(Objects.requireNonNull(reader.snapshotState(checkpointId), "Reader 快照不能为空"));
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
            throw new IllegalStateException("SourceReader 尚未初始化");
        }
    }

    private void ensureStarted() {
        ensureInitialized();
        if (!started) {
            throw new IllegalStateException("SourceReader 尚未启动");
        }
    }
}
