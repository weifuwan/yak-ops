package io.yak.ops.core.api.connector.sink;

import java.util.Collection;

/** Commits prepared external transactions after checkpoint durability is established. */
public interface Committer<CommT> extends AutoCloseable {

    /**
     * Commits complete prepared transactions. Recovery retries committables from a completed
     * checkpoint; connectors must distinguish verified recovery from an unknown fresh outcome.
     *
     * @param recovered true only when replaying a committable restored from durable state
     */
    void commit(Collection<CommT> committables, boolean recovered) throws Exception;

    /**
     * Reconciles abandoned prepared transactions when restoring a task. Only transactions
     * provably owned by this task may be rolled back; keep lists the committables in the
     * latest successfully persisted checkpoint.
     */
    default void recover(Collection<CommT> keep) throws Exception {}

    @Override
    void close() throws Exception;
}
