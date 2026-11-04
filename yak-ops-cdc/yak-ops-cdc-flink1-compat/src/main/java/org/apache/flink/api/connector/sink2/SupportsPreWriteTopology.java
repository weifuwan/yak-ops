package org.apache.flink.api.connector.sink2;

import org.apache.flink.annotation.Public;
import org.apache.flink.streaming.api.connector.sink2.WithPreWriteTopology;
import org.apache.flink.streaming.api.datastream.DataStream;

/**
 * Compatibility adapter for Flink 1.18. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
@Public
public interface SupportsPreWriteTopology<InputT> extends WithPreWriteTopology<InputT> {
    /**
     * Adds an arbitrary topology before the writer. The topology may be used to repartition the
     * data.
     *
     * @param inputDataStream the stream of input records.
     * @return the custom topology before {@link SinkWriter}.
     */
    DataStream<InputT> addPreWriteTopology(DataStream<InputT> inputDataStream);
}
