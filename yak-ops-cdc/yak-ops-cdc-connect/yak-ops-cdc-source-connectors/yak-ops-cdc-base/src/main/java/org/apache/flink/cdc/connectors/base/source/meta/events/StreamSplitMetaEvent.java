package org.apache.flink.cdc.connectors.base.source.meta.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.base.source.enumerator.IncrementalSourceEnumerator;
import org.apache.flink.cdc.connectors.base.source.meta.split.FinishedSnapshotSplitInfo;
import org.apache.flink.cdc.connectors.base.source.reader.IncrementalSourceReader;

import javax.annotation.Nullable;

import java.util.List;

/**
 * The {@link SourceEvent} that {@link IncrementalSourceEnumerator} sends to {@link
 * IncrementalSourceReader} to pass change log metadata, i.e. {@link FinishedSnapshotSplitInfo}.
 */
public class StreamSplitMetaEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    private final String splitId;

    /** The metadata of stream split is divided to multiple groups. */
    private final int metaGroupId;

    /**
     * The serialized metadata of stream split, it's serialized/deserialized by {@link
     * FinishedSnapshotSplitInfo#serialize()} and {@link
     * FinishedSnapshotSplitInfo#deserialize(byte[])}.
     */
    private final List<byte[]> metaGroup;

    private final int totalFinishedSplitSize;

    /**
     * Generation of the stream-split assignment these meta groups belong to (FLINK-40697). The
     * reader echoes it back in {@link StreamSplitMetaAssembledEvent} so the coordinator can reject
     * a stale assembled report from a failed reader attempt.
     */
    private final int assignmentGeneration;

    public StreamSplitMetaEvent(
            String splitId,
            int metaGroupId,
            @Nullable List<byte[]> metaGroup,
            int totalFinishedSplitSize) {
        this(splitId, metaGroupId, metaGroup, totalFinishedSplitSize, 0);
    }

    public StreamSplitMetaEvent(
            String splitId,
            int metaGroupId,
            @Nullable List<byte[]> metaGroup,
            int totalFinishedSplitSize,
            int assignmentGeneration) {
        this.splitId = splitId;
        this.metaGroupId = metaGroupId;
        this.metaGroup = metaGroup;
        this.totalFinishedSplitSize = totalFinishedSplitSize;
        this.assignmentGeneration = assignmentGeneration;
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

    public int getAssignmentGeneration() {
        return assignmentGeneration;
    }
}
