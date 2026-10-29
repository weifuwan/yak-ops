package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.TableId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A serializer for the {@link MySqlSplit}.
 *
 * <p>Snapshot and binlog splits share one entry point, as in Flink CDC. The schema-history
 * remains part of the binlog recovery state; snapshot checkpoints contain only
 * split metadata, never a frozen copy of the source row images.
 */
public final class MySqlSplitSerializer implements SimpleVersionedSerializer<MySqlSplit> {

    public static final MySqlSplitSerializer INSTANCE = new MySqlSplitSerializer();

    // Flink CDC's split layout uses the same two kind flags, but the payload uses YakFlow's
    // Debezium 2.7 history and typed Chunk representation. Do not claim Flink v4 wire compatibility.
    private static final int VERSION = 3;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int SNAPSHOT_SPLIT_FLAG = 1;
    private static final int BINLOG_SPLIT_FLAG = 2;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(MySqlSplit split) throws IOException {
        Objects.requireNonNull(split, "split");
        // Flink CDC caches immutable split bytes. Defensively copy the cached buffer to prevent
        // callers from modifying a later checkpoint or mutating serialized schema history.
        if (split.serializedFormCache != null) {
            return split.serializedFormCache.clone();
        }
        try (var buffer = new ByteArrayOutputStream();
                var out = new DataOutputStream(buffer)) {
            if (split.isSnapshotSplit()) {
                out.writeInt(SNAPSHOT_SPLIT_FLAG);
                writeSnapshot(out, split.asSnapshotSplit());
            } else if (split.isBinlogSplit()) {
                out.writeInt(BINLOG_SPLIT_FLAG);
                writeBinlog(out, split.asBinlogSplit());
            } else {
                throw new IOException("Unknown MySQL split: " + split.getClass().getName());
            }
            out.flush();
            if (buffer.size() > MAX_BYTES) {
                throw new IOException("MySQL split checkpoint exceeds size limit");
            }
            byte[] result = buffer.toByteArray();
            split.serializedFormCache = result;
            return result.clone();
        }
    }

    @Override
    public MySqlSplit deserialize(int version, byte[] serialized) throws IOException {
        return deserializeSplit(version, serialized);
    }

    /** Flink CDC's named split-deserialization entry point. */
    public MySqlSplit deserializeSplit(int version, byte[] serialized) throws IOException {
        if (version != VERSION
                || serialized == null
                || serialized.length < Integer.BYTES
                || serialized.length > MAX_BYTES) {
            throw new IOException("Unsupported MySQL split checkpoint version or size: " + version);
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(serialized))) {
            MySqlSplit split =
                    switch (in.readInt()) {
                        case SNAPSHOT_SPLIT_FLAG -> readSnapshot(in);
                        case BINLOG_SPLIT_FLAG -> readBinlog(in);
                        default -> throw new IOException("Unknown MySQL split kind");
                    };
            if (in.available() != 0) {
                throw new IOException("Trailing bytes in MySQL split checkpoint");
            }
            return split;
        } catch (RuntimeException error) {
            throw new IOException("Invalid MySQL split checkpoint", error);
        }
    }

    /** Snapshot checkpoints store only boundaries and completed high watermarks. */
    private static void writeSnapshot(DataOutputStream out, MySqlSnapshotSplit split) throws IOException {
        if (split.output() != null || split.outputPosition() != 0 || split.lastEmittedKey() != null) {
            throw new IOException("Normalized snapshot row output cannot be stored in a split checkpoint");
        }
        out.writeUTF(split.splitId());
        out.writeUTF(split.tableId().catalog() == null ? "" : split.tableId().catalog());
        out.writeUTF(split.tableId().table());
        writeLong(out, split.lowerInclusive());
        writeLong(out, split.upperExclusive());
        out.writeBoolean(split.finished() != null);
        if (split.finished() != null) {
            split.finished().highWatermark().writeTo(out);
            byte[] history = split.finishedSchemaHistory();
            if (history.length > MAX_BYTES - 4096) {
                throw new IOException("Completed Snapshot schema history exceeds split size limit");
            }
            out.writeInt(history.length);
            out.write(history);
        }
        ChunkRangeStateSerializer.write(out, split.chunkRange());
    }

    private static MySqlSnapshotSplit readSnapshot(DataInputStream in) throws IOException {
        String id = in.readUTF();
        TableId table = new TableId(in.readUTF(), null, in.readUTF());
        Long lower = readLong(in);
        Long upper = readLong(in);
        DebeziumOffset high = in.readBoolean() ? DebeziumOffset.readFrom(in) : null;
        byte[] history = new byte[0];
        if (high != null) {
            int length = in.readInt();
            if (length < 0 || length > in.available() || length > MAX_BYTES - 4096) {
                throw new IOException("Invalid completed Snapshot schema history length");
            }
            history = in.readNBytes(length);
        }
        ChunkRange range = ChunkRangeStateSerializer.read(in);
        FinishedSnapshotSplitInfo finished =
                high == null ? null : new FinishedSnapshotSplitInfo(table, id, lower, upper, high, range);
        return new MySqlSnapshotSplit(id, table, lower, upper, null, null, 0, finished, range, history);
    }

    private static void writeBinlog(DataOutputStream out, MySqlBinlogSplit split) throws IOException {
        out.writeBoolean(split.offset() != null);
        if (split.offset() != null) {
            split.offset().writeTo(out);
        }
        byte[] history = split.schemaHistory();
        if (history.length > MAX_BYTES) {
            throw new IOException("MySQL schema history exceeds checkpoint limit");
        }
        out.writeInt(history.length);
        out.write(history);
        out.writeByte(split.phase().ordinal());
        out.writeBoolean(split.highWatermark() != null);
        if (split.highWatermark() != null) {
            split.highWatermark().writeTo(out);
        }
        writeFinishedSplits(out, split.finishedSplits());
        out.writeInt(split.getTotalFinishedSplitSize());
    }

    private static MySqlBinlogSplit readBinlog(DataInputStream in) throws IOException {
        DebeziumOffset offset = in.readBoolean() ? DebeziumOffset.readFrom(in) : null;
        int length = in.readInt();
        if (length < 0 || length > in.available() || length > MAX_BYTES) {
            throw new IOException("Invalid MySQL schema history length");
        }
        byte[] history = in.readNBytes(length);
        if (offset != null && history.length == 0) {
            throw new IOException("Restored MySQL binlog offset is missing schema history");
        }
        int phase = in.readUnsignedByte();
        if (phase >= MySqlBinlogSplit.Phase.values().length) {
            throw new IOException("Unknown MySQL Binlog phase");
        }
        DebeziumOffset high = in.readBoolean() ? DebeziumOffset.readFrom(in) : null;
        List<FinishedSnapshotSplitInfo> infos = readFinishedSplits(in);
        int totalFinishedSplitSize = in.readInt();
        return MySqlBinlogSplit.restore(
                offset, history, MySqlBinlogSplit.Phase.values()[phase], high, infos, totalFinishedSplitSize);
    }

    /** Serializes completed snapshot chunk watermarks for reader and enumerator checkpoints. */
    public static void writeFinishedSplits(DataOutputStream out, List<FinishedSnapshotSplitInfo> finished)
            throws IOException {
        if (finished.size() > 10_000) {
            throw new IOException("Too many finished MySQL snapshot splits");
        }
        out.writeInt(finished.size());
        for (FinishedSnapshotSplitInfo info : finished) {
            out.writeUTF(info.tableId().catalog() == null ? "" : info.tableId().catalog());
            out.writeUTF(info.tableId().table());
            out.writeUTF(info.splitId());
            writeLong(out, info.lowerInclusive());
            writeLong(out, info.upperExclusive());
            info.highWatermark().writeTo(out);
            ChunkRangeStateSerializer.write(out, info.chunkRange());
        }
    }

    public static List<FinishedSnapshotSplitInfo> readFinishedSplits(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > 10_000 || size > in.available()) {
            throw new IOException("Invalid finished MySQL snapshot split count");
        }
        List<FinishedSnapshotSplitInfo> result = new ArrayList<>(size);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < size; i++) {
            TableId table = new TableId(in.readUTF(), null, in.readUTF());
            String id = in.readUTF();
            if (!ids.add(id)) {
                throw new IOException("Duplicate finished MySQL snapshot split: " + id);
            }
            Long lower = readLong(in);
            Long upper = readLong(in);
            DebeziumOffset high = DebeziumOffset.readFrom(in);
            ChunkRange range = ChunkRangeStateSerializer.read(in);
            result.add(new FinishedSnapshotSplitInfo(table, id, lower, upper, high, range));
        }
        return List.copyOf(result);
    }

    private static void writeLong(DataOutputStream out, Long value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeLong(value);
        }
    }

    private static Long readLong(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readLong() : null;
    }
}
