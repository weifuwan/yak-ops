package io.yak.ops.core.api.connector.sink;

import java.util.Collection;

/**
 * Writer that precommits transactions at an aligned checkpoint.
 *
 * <p>The returned values describe prepared transactions; they do not commit external data.
 * The runtime stores them with the Source checkpoint and invokes the Committer only after
 * durable checkpoint publication. This interface does not imply that ordinary flush commits.
 */
public interface CommittingSinkWriter<T, CommT> extends SinkWriter<T> {

    /**
     * Prepares the current transaction and begins a new one before accepting later records.
     * Called by the owning mailbox after flush(false), once per checkpoint.
     */
    Collection<CommT> prepareCommit(long checkpointId) throws Exception;
}
