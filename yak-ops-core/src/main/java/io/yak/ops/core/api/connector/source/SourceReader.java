package io.yak.ops.core.api.connector.source;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Reader of splits assigned to one parallel Source subtask.
 *
 * <p>{@link #pollNext(ReaderOutput)} must not block the task mailbox on long-running I/O.
 * When no data is ready, return {@link InputStatus#NOTHING_AVAILABLE} and expose a
 * completion signal through {@link #isAvailable()}. All lifecycle and input callbacks are
 * serialized by the runtime on the owning task thread.
 *
 * <p>Snapshots contain unfinished splits with their current progress. An empty snapshot
 * does not by itself mean that no further splits can be assigned.
 *
 * @param <T> the emitted record type
 * @param <SplitT> the split type assigned to this reader
 * @author weifuwan
 */
public interface SourceReader<T, SplitT extends SourceSplit> extends AutoCloseable {

    /** Starts this reader; restored splits may already have been assigned. */
    void start() throws Exception;

    /**
     * Polls available records without blocking the task mailbox.
     *
     * <p>Return {@link InputStatus#NOTHING_AVAILABLE} to suspend input, or
     * {@link InputStatus#END_OF_INPUT} only when all work is finished and no new splits will arrive.
     * Limit work per poll so control messages do not starve.
     *
     * @param output receives records emitted during this step
     * @return the input availability status after the current step
     * @throws Exception if reading or delivering a record fails
     */
    InputStatus pollNext(ReaderOutput<T> output) throws Exception;

    /**
     * Returns a future that completes when input can make progress.
     *
     * <p>An unready reader must not continually return an already completed future, which
     * would cause a busy loop. New splits and terminal signals must wake pending waiters.
     *
     * @return the next input-availability signal
     */
    CompletableFuture<Void> isAvailable();

    /**
     * Adds newly assigned or restored splits.
     *
     * @param splits the work units, including progress for restored splits
     * @throws Exception if a split cannot be accepted
     */
    void addSplits(List<SplitT> splits) throws Exception;

    /** Signals that no further splits will be assigned; active splits may still be processing. */
    void notifyNoMoreSplits();

    /** Handles an event from the enumerator on the reader's mailbox thread. */
    default void handleSourceEvents(SourceEvent event) throws Exception {}

    /**
     * Captures unfinished split state for a checkpoint.
     *
     * <p>Returned state must not mutate when the reader resumes; the runtime persists
     * its serialized representation.
     *
     * @param checkpointId the checkpoint being captured
     * @return split snapshots containing their current progress
     * @throws Exception if snapshot creation fails
     */
    List<SplitT> snapshotState(long checkpointId) throws Exception;

    /** Notifies the reader after a checkpoint is durably completed, allowing offset confirmation. */
    default void notifyCheckpointComplete(long checkpointId) throws Exception {}

    /** Releases reader resources, including background I/O, on finish, cancellation or failure. */
    @Override
    void close() throws Exception;
}
