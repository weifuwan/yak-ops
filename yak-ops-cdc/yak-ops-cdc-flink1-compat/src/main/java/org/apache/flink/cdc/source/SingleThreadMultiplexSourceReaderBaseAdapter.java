package org.apache.flink.cdc.source;

import org.apache.flink.api.connector.source.SourceReaderContext;
import org.apache.flink.api.connector.source.SourceSplit;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.base.source.reader.RecordEmitter;
import org.apache.flink.connector.base.source.reader.RecordsWithSplitIds;
import org.apache.flink.connector.base.source.reader.SingleThreadMultiplexSourceReaderBase;
import org.apache.flink.connector.base.source.reader.fetcher.SingleThreadFetcherManager;
import org.apache.flink.connector.base.source.reader.synchronization.FutureCompletingBlockingQueue;

/**
 * Compatibility adapter for Flink 1.18. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
public abstract class SingleThreadMultiplexSourceReaderBaseAdapter<
                E, T, SplitT extends SourceSplit, SplitStateT>
        extends SingleThreadMultiplexSourceReaderBase<E, T, SplitT, SplitStateT> {
    public SingleThreadMultiplexSourceReaderBaseAdapter(
            FutureCompletingBlockingQueue<RecordsWithSplitIds<E>> elementsQueue,
            SingleThreadFetcherManager<E, SplitT> splitFetcherManager,
            RecordEmitter<E, T, SplitStateT> recordEmitter,
            Configuration config,
            SourceReaderContext context) {
        super(elementsQueue, splitFetcherManager, recordEmitter, config, context);
    }
}
