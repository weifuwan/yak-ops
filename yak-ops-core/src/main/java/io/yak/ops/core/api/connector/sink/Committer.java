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

    @Override
    void close() throws Exception;
}
