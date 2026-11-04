package org.apache.flink.cdc.connectors.base.source.assigner.splitter;

import javax.annotation.Nullable;

import java.util.Objects;

import static org.apache.flink.util.Preconditions.checkArgument;

/**
 * An internal structure describes a chunk range with a chunk start (inclusive) and chunk end
 * (exclusive). Note that {@code null} represents unbounded chunk start/end.
 */
public class ChunkRange {
    private final @Nullable Object chunkStart;
    private final @Nullable Object chunkEnd;

    /**
     * Returns a {@link ChunkRange} which represents a full table scan with unbounded chunk start
     * and chunk end.
     */
    public static ChunkRange all() {
        return new ChunkRange(null, null);
    }

    /** Returns a {@link ChunkRange} with the given chunk start and chunk end. */
    public static ChunkRange of(Object chunkStart, Object chunkEnd) {
        return new ChunkRange(chunkStart, chunkEnd);
    }

    private ChunkRange(@Nullable Object chunkStart, @Nullable Object chunkEnd) {
        if (chunkStart != null || chunkEnd != null) {
            checkArgument(
                    !Objects.equals(chunkStart, chunkEnd),
                    "Chunk start %s shouldn't be equal to chunk end %s",
                    chunkStart,
                    chunkEnd);
        }
        this.chunkStart = chunkStart;
        this.chunkEnd = chunkEnd;
    }

    @Nullable
    public Object getChunkStart() {
        return chunkStart;
    }

    @Nullable
    public Object getChunkEnd() {
        return chunkEnd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChunkRange that = (ChunkRange) o;
        return Objects.equals(chunkStart, that.chunkStart)
                && Objects.equals(chunkEnd, that.chunkEnd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkStart, chunkEnd);
    }

    @Override
    public String toString() {
        return "ChunkRange{chunkStart=" + chunkStart + ", chunkEnd=" + chunkEnd + '}';
    }
}
