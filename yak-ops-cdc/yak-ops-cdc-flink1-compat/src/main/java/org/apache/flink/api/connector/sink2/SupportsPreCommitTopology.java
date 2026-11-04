package org.apache.flink.api.connector.sink2;

import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.flink.streaming.api.connector.sink2.CommittableMessage;
import org.apache.flink.streaming.api.datastream.DataStream;

/**
 * Compatibility adapter for Flink 1.18. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
public interface SupportsPreCommitTopology<WriterResultT, CommittableT> {
    DataStream<CommittableMessage<CommittableT>> addPreCommitTopology(
            DataStream<CommittableMessage<WriterResultT>> var1);

    SimpleVersionedSerializer<WriterResultT> getWriteResultSerializer();
}
