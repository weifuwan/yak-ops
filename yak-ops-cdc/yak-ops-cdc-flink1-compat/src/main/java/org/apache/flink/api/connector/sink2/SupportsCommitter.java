package org.apache.flink.api.connector.sink2;

import org.apache.flink.core.io.SimpleVersionedSerializer;

import java.io.IOException;

/**
 * Compatibility adapter for Flink 1.18. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
public interface SupportsCommitter<CommittableT> {

    Committer<CommittableT> createCommitter(CommitterInitContext context) throws IOException;

    SimpleVersionedSerializer<CommittableT> getCommittableSerializer();
}
