package org.apache.flink.connector.base.sink;

import org.apache.flink.api.connector.sink2.StatefulSinkWriterAdapter;
import org.apache.flink.api.connector.sink2.WriterInitContext;
import org.apache.flink.connector.base.sink.writer.BufferedRequestState;
import org.apache.flink.connector.base.sink.writer.ElementConverter;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;

/**
 * Compatibility adapter for Flink 1.20. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
public abstract class AsyncSinkBaseAdapter<InputT, RequestEntryT extends Serializable>
        extends AsyncSinkBase<InputT, RequestEntryT> {
    protected AsyncSinkBaseAdapter(
            ElementConverter<InputT, RequestEntryT> elementConverter,
            int maxBatchSize,
            int maxInFlightRequests,
            int maxBufferedRequests,
            long maxBatchSizeInBytes,
            long maxTimeInBufferMS,
            long maxRecordSizeInBytes) {
        super(
                elementConverter,
                maxBatchSize,
                maxInFlightRequests,
                maxBufferedRequests,
                maxBatchSizeInBytes,
                maxTimeInBufferMS,
                maxRecordSizeInBytes);
    }

    @Override
    public StatefulSinkWriter<InputT, BufferedRequestState<RequestEntryT>> restoreWriter(
            WriterInitContext context,
            Collection<BufferedRequestState<RequestEntryT>> recoveredState)
            throws IOException {
        return restoreWriterAdapter(context, recoveredState);
    }

    public abstract StatefulSinkWriterAdapter<InputT, BufferedRequestState<RequestEntryT>>
            restoreWriterAdapter(
                    WriterInitContext context,
                    Collection<BufferedRequestState<RequestEntryT>> recoveredState)
                    throws IOException;
}
