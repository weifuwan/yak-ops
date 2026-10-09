package io.yak.ops.core.api.connector.source;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

/**
 * Runtime-owned coordination services exposed to a SplitEnumerator.
 *
 * <p>The runtime manages reader registration, split delivery acknowledgments, failed
 * assignments and checkpoint handoff. The Connector chooses which work to assign.
 *
 * <p>Enumerator callbacks and context state changes execute on the coordinator thread.
 * Asynchronous discovery results must be handed back to that thread.
 *
 * @param <SplitT> the split type being assigned
 * @author weifuwan
 */
public interface SplitEnumeratorContext<SplitT extends SourceSplit> {

    /** Returns the resolved parallelism of the source operator. */
    int currentParallelism();

    /** Returns an immutable snapshot of registered reader subtask IDs. */
    Set<Integer> registeredReaders();

    /**
     * Assigns a split to a reader while tracking delivery and acknowledgment.
     *
     * <p>The runtime must retain enough assignment history to avoid losing work between
     * Enumerator and Reader state after a failure.
     *
     * @param split the work unit to assign
     * @param subtaskId the destination reader's subtask index
     */
    void assignSplit(SplitT split, int subtaskId);

    /**
     * Signals that a reader will not receive further splits.
     *
     * <p>This does not imply that previously assigned splits are finished.
     */
    void signalNoMoreSplits(int subtaskId);

    /** Sends a connector-defined event to the registered reader attempt. */
    default void sendEventToSourceReader(int subtaskId, SourceEvent event) {
        throw new UnsupportedOperationException("This context does not support SourceEvent transport");
    }

    /**
     * Runs potentially blocking discovery off the coordinator thread.
     *
     * <p>The handler runs back on the coordinator thread, and background actions must not
     * mutate shared enumerator state.
     *
     * @param action the background discovery action
     * @param handler receives the result or failure on the coordinator thread
     * @param <T> the discovery result type
     */
    <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler);

    /** Schedules an external action on the coordinator thread without long blocking work. */
    void runInCoordinatorThread(Runnable action);
}
