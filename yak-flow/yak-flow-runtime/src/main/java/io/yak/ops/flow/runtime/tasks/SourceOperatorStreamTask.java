package io.yak.ops.flow.runtime.tasks;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.io.RecordWriterOutput;
import io.yak.ops.flow.runtime.io.StreamTaskSourceInput;
import io.yak.ops.flow.runtime.operators.OperatorChain;
import io.yak.ops.flow.runtime.operators.SourceOperator;
import io.yak.ops.flow.runtime.operators.SourceReaderRuntimeContext;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;
import io.yak.ops.flow.runtime.operators.coordination.SubtaskGateway;
import io.yak.ops.flow.runtime.source.coordinator.SourceCoordinator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * StreamTask that runs one SourceOperator on its mailbox thread.
 *
 * <p>The coordinator sends Split events and receives acknowledgment only after the
 * SourceReader has handled them. Reader lifecycle and input processing are serialized
 * on the owning task thread; no second reader thread is created.
 *
 * <p>Output is sent through ReaderOutput, RecordWriterOutput or an inline OperatorChain.
 * Global checkpoint coordination belongs to the runtime checkpoint coordinator.
 */
public final class SourceOperatorStreamTask<T, SplitT extends SourceSplit> extends StreamTask
        implements SubtaskGateway {

    private final SourceCoordinator<SplitT, ?> coordinator;
    private final SourceOperator<T, SplitT> operator;
    private final StreamTaskSourceInput<T> input;
    private final ReaderOutput<T> output;
    private final OperatorChain operatorChain;
    private final RecordWriterOutput<?> recordWriter;
    private final List<SplitT> restoredSplits;
    private boolean pausedForCheckpoint;
    private CompletableFuture<Void> resumeFuture = CompletableFuture.completedFuture(null);

    public SourceOperatorStreamTask(
            Source<T, SplitT, ?> source,
            SourceCoordinator<SplitT, ?> coordinator,
            TaskEnvironment environment,
            ReaderOutput<T> output) {
        this(source, coordinator, environment, output, null, List.of());
    }

    /** Initializes an inline operator chain without allocating an additional task thread. */
    public SourceOperatorStreamTask(
            Source<T, SplitT, ?> source,
            SourceCoordinator<SplitT, ?> coordinator,
            TaskEnvironment environment,
            ReaderOutput<T> output,
            OperatorChain operatorChain) {
        this(source, coordinator, environment, output, operatorChain, List.of());
    }

    /** Restores unfinished split progress before initializing the Reader in the mailbox. */
    public SourceOperatorStreamTask(
            Source<T, SplitT, ?> source,
            SourceCoordinator<SplitT, ?> coordinator,
            TaskEnvironment environment,
            ReaderOutput<T> output,
            OperatorChain operatorChain,
            List<SplitT> restoredSplits) {
        super(environment);
        this.restoredSplits = List.copyOf(Objects.requireNonNull(restoredSplits, "restoredSplits 不能为空"));
        this.operatorChain = operatorChain;
        this.output = Objects.requireNonNull(output, "output 不能为空");
        this.recordWriter = output instanceof RecordWriterOutput<?> writer ? writer : null;
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator 不能为空");
        this.coordinator.coordinatorContext().validateTask(taskInfo());
        SourceReaderRuntimeContext readerContext = new SourceReaderRuntimeContext(
                taskEnvironment(), event -> coordinator.handleEventFromOperator(taskInfo(), event), this::failAsync);
        this.operator = new SourceOperator<>(source, readerContext);
        this.input = new StreamTaskSourceInput<>(operator);
    }

    @Override
    protected void openTask() throws Exception {
        if (operatorChain != null) {
            operatorChain.open(taskEnvironment());
        }
        operator.initialize();
        operator.restoreSplits(restoredSplits);
        // Register the gateway before starting the Reader, which may immediately request splits.
        coordinator.registerReader(taskInfo(), this).get();
        operator.start();
    }

    @Override
    protected InputStatus processInput() throws Exception {
        return pausedForCheckpoint ? InputStatus.NOTHING_AVAILABLE : input.emitNext(output);
    }

    @Override
    protected CompletableFuture<Void> getAvailableFuture() {
        return pausedForCheckpoint ? resumeFuture : input.getAvailableFuture();
    }

    @Override
    protected void finishTask() throws Exception {
        if (operatorChain != null) {
            operatorChain.finish();
        }
        if (recordWriter != null) {
            recordWriter.finish();
        }
    }

    @Override
    protected void closeTask() throws Exception {
        try (OperatorChain chain = operatorChain;
                SourceOperator<T, SplitT> reader = operator) {
            // Close the Reader before its upstream chain; resource cleanup preserves suppressed failures.
        }
    }

    /** Propagates asynchronous coordinator failure to the task mailbox for lifecycle cleanup. */
    public void coordinatorFailed(Throwable failure) {
        failAsync(failure);
    }

    @Override
    protected void taskFailed(Throwable failure) {
        coordinator.readerFailed(taskInfo(), failure);
    }

    /**
     * Delivers a coordinator event on the task mailbox thread.
     *
     * <p>The returned stage completes after the SourceOperator processes the event, not
     * merely after enqueueing it. This does not imply that records were consumed or a
     * checkpoint was completed.
     */
    @Override
    public CompletionStage<Void> sendEvent(OperatorEvent event) {
        Objects.requireNonNull(event, "event 不能为空");
        return submitMailbox(() -> {
            operator.handleOperatorEvent(event);
            // Older/source-specific Reader implementations need not complete isAvailable()
            // when a new split arrives. A coordinator event still makes input worth rechecking.
            resumeInputProcessing();
            return null;
        });
    }

    /**
     * Captures the Reader's progress and pauses input on the task mailbox.
     *
     * <p>The coordinator must already have frozen new split requests and assignments.
     * Control events can still run while data polling is paused.
     */
    public CompletableFuture<List<SplitT>> pauseForCheckpoint(long checkpointId) {
        return submitMailbox(() -> {
            if (pausedForCheckpoint) {
                throw new IllegalStateException("Reader 已被另一个 Checkpoint 暂停");
            }
            List<SplitT> state = operator.snapshotState(checkpointId);
            pausedForCheckpoint = true;
            resumeFuture = new CompletableFuture<>();
            return state;
        });
    }

    /** Capture the reader and append an ordered barrier before releasing the source mailbox. */
    public CompletableFuture<List<SplitT>> pauseAndEmitBarrier(long checkpointId) {
        if (recordWriter == null) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("Checkpointed source needs a physical ResultPartition"));
        }
        return submitMailbox(() -> {
            if (pausedForCheckpoint) {
                throw new IllegalStateException("Reader is already paused for checkpoint");
            }
            List<SplitT> state = operator.snapshotState(checkpointId);
            pausedForCheckpoint = true;
            resumeFuture = new CompletableFuture<>();
            recordWriter.broadcastBarrier(checkpointId);
            return state;
        });
    }

    /** Resumes Source input polling after checkpoint success or failure. */
    public CompletableFuture<Void> resumeAfterCheckpoint() {
        return submitMailbox(() -> {
            if (pausedForCheckpoint) {
                pausedForCheckpoint = false;
                resumeFuture.complete(null);
                // The mailbox may still be suspended on a pre-checkpoint Reader availability Future.
                // Explicitly recheck the input after the coordinated cut resumes.
                resumeInputProcessing();
            }
            return null;
        });
    }

    /** Captures Reader-local progress; this is not a completed global checkpoint. */
    public CompletableFuture<List<SplitT>> snapshotState(long checkpointId) {
        return submitMailbox(() -> operator.snapshotState(checkpointId));
    }

    /** Notifies the Reader only after the full job checkpoint was completed. */
    public CompletableFuture<Void> notifyCheckpointComplete(long checkpointId) {
        return submitMailbox(() -> {
            operator.notifyCheckpointComplete(checkpointId);
            // Completing a checkpoint can make the Reader ready even if isAvailable()
            // was previously suspended; re-enter the mailbox input action.
            resumeInputProcessing();
            return null;
        });
    }
}
