package org.apache.flink.api.connector.sink2;

/**
 * Compatibility adapter for Flink 1.20. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
public interface StatefulSinkWriterAdapter<InputT, WriterStateT>
        extends StatefulSink.StatefulSinkWriter<InputT, WriterStateT> {}
