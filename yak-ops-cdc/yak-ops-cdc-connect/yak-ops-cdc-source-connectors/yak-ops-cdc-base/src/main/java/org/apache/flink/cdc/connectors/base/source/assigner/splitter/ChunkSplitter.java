package org.apache.flink.cdc.connectors.base.source.assigner.splitter;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.connectors.base.source.assigner.state.ChunkSplitterState;
import org.apache.flink.cdc.connectors.base.source.meta.split.SnapshotSplit;

import io.debezium.relational.TableId;

import java.util.Collection;

/** The splitter used to split collection into a set of chunks. */
@Experimental
public interface ChunkSplitter {

    /**
     * Called to open the chunk splitter to acquire any resources, like threads or jdbc connections.
     */
    void open();

    /** Generates all snapshot splits (chunks) for the give data collection. */
    Collection<SnapshotSplit> generateSplits(TableId tableId) throws Exception;

    /** Get whether the splitter has more chunks for current table. */
    boolean hasNextChunk();

    /**
     * Creates a snapshot of the state of this chunk splitter, to be stored in a checkpoint.
     *
     * <p>This method takes the ID of the checkpoint for which the state is snapshotted. Most
     * implementations should be able to ignore this parameter, because for the contents of the
     * snapshot, it doesn't matter for which checkpoint it gets created. This parameter can be
     * interesting for source connectors with external systems where those systems are themselves
     * aware of checkpoints; for example in cases where the enumerator notifies that system about a
     * specific checkpoint being triggered.
     *
     * @param checkpointId The ID of the checkpoint for which the snapshot is created.
     * @return an object containing the state of the split enumerator.
     */
    ChunkSplitterState snapshotState(long checkpointId);

    TableId getCurrentSplittingTableId();

    /**
     * Called to open the chunk splitter to release any resources, like threads or jdbc connections.
     */
    void close() throws Exception;
}
