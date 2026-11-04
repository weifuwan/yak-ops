package org.apache.flink.cdc.connectors.mysql.source.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.mysql.source.enumerator.MySqlSourceEnumerator;
import org.apache.flink.cdc.connectors.mysql.source.reader.MySqlSourceReader;
import org.apache.flink.cdc.connectors.mysql.source.split.MySqlBinlogSplit;

/**
 * The {@link SourceEvent} that {@link MySqlSourceReader} sends to {@link MySqlSourceEnumerator}
 * once it holds a complete {@link MySqlBinlogSplit}, signalling that all finished snapshot split
 * metadata has been assembled by the reader. This lets the enumerator release the snapshot split
 * metadata it retains. Idempotent: re-sent whenever a complete binlog split (re-)enters reading.
 *
 * <p>Carries the generation the reader assembled under, or {@link
 * #COMPLETE_WITHOUT_META_GENERATION} for an inline split. The coordinator uses it to drop a stale
 * event left over from a failed reader attempt, which would otherwise arm the release before a
 * freshly-assigned reader has re-assembled.
 */
public class BinlogSplitMetaAssembledEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Reported instead of a real generation when the reader held a complete split inline and never
     * requested meta groups. The coordinator releases on it regardless of generation, since an
     * inline reader never needs the coordinator's metadata.
     */
    public static final long COMPLETE_WITHOUT_META_GENERATION = -1L;

    private final long binlogAssignmentGeneration;

    public BinlogSplitMetaAssembledEvent(long binlogAssignmentGeneration) {
        this.binlogAssignmentGeneration = binlogAssignmentGeneration;
    }

    public long getBinlogAssignmentGeneration() {
        return binlogAssignmentGeneration;
    }
}
