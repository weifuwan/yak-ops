package org.apache.flink.connector.base.sink.writer;

import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.connector.base.sink.writer.config.AsyncSinkWriterConfiguration;

import java.io.Serializable;
import java.util.Collection;

/**
 * Compatibility adapter for Flink 1.20. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
public abstract class AsyncSinkWriterAdapter<InputT, RequestEntryT extends Serializable>
        extends AsyncSinkWriter<InputT, RequestEntryT> {

    public AsyncSinkWriterAdapter(
            ElementConverter<InputT, RequestEntryT> elementConverter,
            Sink.InitContext context,
            AsyncSinkWriterConfiguration configuration,
            Collection<BufferedRequestState<RequestEntryT>> bufferedRequestStates) {
        super(elementConverter, context, configuration, bufferedRequestStates);
    }
}
