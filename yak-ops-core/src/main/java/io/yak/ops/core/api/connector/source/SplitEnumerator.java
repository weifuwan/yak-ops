package io.yak.ops.core.api.connector.source;

import java.util.List;

/**
* Connector instance that discovers and assigns source splits to readers.
*
* <p>Splits are assigned through {@link SplitEnumeratorContext}, not polled through a
* next-split API. Lifecycle calls run serially on the coordinator thread; potentially
* blocking discovery should use the context's asynchronous facilities.
*
* <p>Assigning a split does not imply that its reader has completed processing it.
*
* @param <SplitT> the assigned split type
* @param <EnumStateT> the enumerator's checkpoint state type
* @author weifuwan
*/
public interface SplitEnumerator<SplitT extends SourceSplit, EnumStateT> extends AutoCloseable {

    /** Starts discovery or assignment once, including after restoration. */
    void start() throws Exception;

    /** Handles a reader's request, assigning available work or waiting for discovery. */
    void handleSplitRequest(int subtaskId) throws Exception;

    /** Notifies the enumerator that a reader has registered. */
    void addReader(int subtaskId) throws Exception;

    /** Handles an attempt-validated reader event on the coordinator thread. */
    default void handleSourceEvent(int subtaskId, SourceEvent event) throws Exception {}

    /**
    * Returns work that needs reassignment following reader failure.
    *
    * <p>The supplied splits must preserve their recovery offsets; replaying from the
    * beginning is valid only if the Connector explicitly supports it.
    *
    * @param splits the unfinished splits to assign again
    * @param subtaskId the subtask that previously owned those splits
    * @throws Exception if the splits cannot be recovered
    */
    void addSplitsBack(List<SplitT> splits, int subtaskId) throws Exception;

    /**
    * Captures enumeration progress and work that has not yet been handed to readers.
    *
    * <p>Progress already owned by a reader is captured by
    * {@link SourceReader#snapshotState(long)} and must not be duplicated here.
    *
    * @param checkpointId the checkpoint being captured
    * @return the enumerator state to persist
    * @throws Exception if snapshot creation fails
    */
    EnumStateT snapshotState(long checkpointId) throws Exception;

    /** Notifies the enumerator when the checkpoint has been durably completed. */
    default void notifyCheckpointComplete(long checkpointId) throws Exception {}

    /** Releases coordinator resources on completion, cancellation or failure. */
    @Override
    void close() throws Exception;
}
