package org.apache.flink.cdc.connectors.mysql.source.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.mysql.source.enumerator.MySqlSourceEnumerator;
import org.apache.flink.cdc.connectors.mysql.source.reader.MySqlSourceReader;
import org.apache.flink.cdc.connectors.mysql.source.split.FinishedSnapshotSplitInfo;

import javax.annotation.Nullable;

import java.util.List;

/**
 * The {@link SourceEvent} that {@link MySqlSourceEnumerator} sends to {@link MySqlSourceReader} to
 * pass binlog metadata, i.e. {@link FinishedSnapshotSplitInfo}.
 */
public class BinlogSplitMetaEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    private final String splitId;

    /** The metadata of binlog split is divided to multiple groups. */
    private final int metaGroupId;

    /**
     * The serialized metadata of binlog split, it's serialized/deserialize by {@link
     * FinishedSnapshotSplitInfo#serialize(FinishedSnapshotSplitInfo)} and {@link
     * FinishedSnapshotSplitInfo#deserialize(byte[])}.
     */
    private final List<byte[]> metaGroup;

    private final int totalFinishedSplitSize;

    /**
     * The binlog assignment generation current at the coordinator when this meta group was served.
     * The reader echoes it back in {@link BinlogSplitMetaAssembledEvent} so the coordinator can
     * tell a fresh assembled signal from a stale one left by a failed reader attempt.
     */
    private final long binlogAssignmentGeneration;

    public BinlogSplitMetaEvent(
            String splitId,
            int metaGroupId,
            @Nullable List<byte[]> metaGroup,
            int totalFinishedSplitSize,
            long binlogAssignmentGeneration) {
        this.splitId = splitId;
        this.metaGroupId = metaGroupId;
        this.metaGroup = metaGroup;
        this.totalFinishedSplitSize = totalFinishedSplitSize;
        this.binlogAssignmentGeneration = binlogAssignmentGeneration;
    }

    public String getSplitId() {
        return splitId;
    }

    public int getMetaGroupId() {
        return metaGroupId;
    }

    public List<byte[]> getMetaGroup() {
        return metaGroup;
    }

    public int getTotalFinishedSplitSize() {
        return totalFinishedSplitSize;
    }

    public long getBinlogAssignmentGeneration() {
        return binlogAssignmentGeneration;
    }
}
