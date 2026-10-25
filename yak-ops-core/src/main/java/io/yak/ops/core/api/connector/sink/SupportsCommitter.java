package io.yak.ops.core.api.connector.sink;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;

/**
 * Optional two-phase-commit capability. Regular Sink implementations remain at-least-once.
 * The runtime owns commit scheduling and persistence, not the Source or JDBC connector.
 */
public interface SupportsCommitter<T, CommT> extends Sink<T> {

    Committer<CommT> createCommitter(WriterInitContext context) throws Exception;

    SimpleVersionedSerializer<CommT> getCommittableSerializer();
}
