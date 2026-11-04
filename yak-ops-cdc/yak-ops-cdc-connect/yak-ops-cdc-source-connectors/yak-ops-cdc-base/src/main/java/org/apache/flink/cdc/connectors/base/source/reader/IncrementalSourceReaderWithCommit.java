package org.apache.flink.cdc.connectors.base.source.reader;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.connectors.base.config.SourceConfig;
import org.apache.flink.cdc.connectors.base.dialect.DataSourceDialect;
import org.apache.flink.cdc.connectors.base.source.meta.offset.Offset;
import org.apache.flink.cdc.connectors.base.source.meta.split.SourceSplitBase;
import org.apache.flink.cdc.connectors.base.source.meta.split.SourceSplitSerializer;
import org.apache.flink.cdc.connectors.base.source.meta.split.StreamSplit;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.base.source.reader.RecordEmitter;
import org.apache.flink.connector.base.source.reader.synchronization.FutureCompletingBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Record the LSN of checkpoint {@link StreamSplit}, which can be used to submit to the CDC source.
 */
@Experimental
public class IncrementalSourceReaderWithCommit extends IncrementalSourceReader {
    private static final Logger LOG =
            LoggerFactory.getLogger(IncrementalSourceReaderWithCommit.class);

    protected final TreeMap<Long, Offset> lastCheckpointOffsets;
    private long maxCompletedCheckpointId;

    public IncrementalSourceReaderWithCommit(
            FutureCompletingBlockingQueue elementQueue,
            Supplier supplier,
            RecordEmitter recordEmitter,
            Configuration config,
            IncrementalSourceReaderContext incrementalSourceReaderContext,
            SourceConfig sourceConfig,
            SourceSplitSerializer sourceSplitSerializer,
            DataSourceDialect dialect) {
        super(
                elementQueue,
                supplier,
                recordEmitter,
                config,
                incrementalSourceReaderContext,
                sourceConfig,
                sourceSplitSerializer,
                dialect);
        this.lastCheckpointOffsets = new TreeMap<>();
        this.maxCompletedCheckpointId = 0;
    }

    @Override
    public List<SourceSplitBase> snapshotState(long checkpointId) {
        final List<SourceSplitBase> stateSplits = super.snapshotState(checkpointId);

        stateSplits.stream()
                .filter(SourceSplitBase::isStreamSplit)
                .findAny()
                .map(SourceSplitBase::asStreamSplit)
                .ifPresent(
                        streamSplit -> {
                            lastCheckpointOffsets.put(
                                    checkpointId, streamSplit.getStartingOffset());
                            LOG.debug(
                                    "Starting offset of stream split is: {}, and checkpoint id is {}.",
                                    streamSplit.getStartingOffset(),
                                    checkpointId);
                        });

        return stateSplits;
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        // checkpointId might be for a checkpoint that was triggered earlier. see
        // CheckpointListener#notifyCheckpointComplete(long).
        if (checkpointId > maxCompletedCheckpointId) {
            Offset offset = lastCheckpointOffsets.get(checkpointId);
            dialect.notifyCheckpointComplete(checkpointId, offset);
            lastCheckpointOffsets.headMap(checkpointId, true).clear();
            maxCompletedCheckpointId = checkpointId;
        }
    }
}
