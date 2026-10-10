package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.TableId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Versioned transport of snapshot ranges and paused/running Binlog checkpoint state.
 *
 * <p>The original stream-only Binlog serializer is unchanged, preserving PR1 checkpoints.
 */
public final class MySqlHybridSplitSerializer implements SimpleVersionedSerializer<MySqlHybridSplit> {

    private static final int VERSION = 1;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final MySqlBinlogSplitSerializer binlogSerializer = new MySqlBinlogSplitSerializer();

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(MySqlHybridSplit split) throws IOException {
        Objects.requireNonNull(split, "split");
        try (var bytes = new ByteArrayOutputStream();
                var out = new DataOutputStream(bytes)) {
            if (split instanceof MySqlSnapshotSplit snapshot) {
                out.writeByte(1);
                out.writeUTF(snapshot.splitId());
                out.writeUTF(snapshot.fingerprint());
                out.writeUTF(snapshot.tableId().catalog() == null ? "" : snapshot.tableId().catalog());
                out.writeUTF(snapshot.tableId().table());
                writeLong(out, snapshot.lowerInclusive());
                writeLong(out, snapshot.upperExclusive());
                writeLong(out, snapshot.lastEmittedKey());
            } else if (split instanceof MySqlHybridBinlogSplit binlog) {
                out.writeByte(2);
                out.writeByte(binlog.phase().ordinal());
                out.writeBoolean(binlog.highWatermark() != null);
                if (binlog.highWatermark() != null) {
                    binlog.highWatermark().writeTo(out);
                }
                byte[] encoded = binlogSerializer.serialize(binlog.binlog());
                out.writeInt(encoded.length);
                out.write(encoded);
            } else {
                throw new IOException("Unknown MySQL hybrid split type");
            }
            out.flush();
            if (bytes.size() > MAX_BYTES) {
                throw new IOException("MySQL hybrid split state exceeds size limit");
            }
            return bytes.toByteArray();
        }
    }

    @Override
    public MySqlHybridSplit deserialize(int version, byte[] serialized) throws IOException {
        if (version != VERSION || serialized == null || serialized.length > MAX_BYTES) {
            throw new IOException("Unsupported MySQL hybrid split version or size");
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(serialized))) {
            MySqlHybridSplit split =
                    switch (in.readUnsignedByte()) {
                        case 1 -> new MySqlSnapshotSplit(
                                in.readUTF(),
                                in.readUTF(),
                                new TableId(in.readUTF(), null, in.readUTF()),
                                readLong(in),
                                readLong(in),
                                readLong(in));
                        case 2 -> {
                            int phase = in.readUnsignedByte();
                            if (phase >= MySqlHybridBinlogSplit.Phase.values().length) {
                                throw new IOException("Unknown hybrid Binlog phase");
                            }
                            BinlogOffset high = in.readBoolean() ? BinlogOffset.readFrom(in) : null;
                            int length = in.readInt();
                            if (length < 0 || length > in.available() || length > MAX_BYTES) {
                                throw new IOException("Invalid hybrid Binlog state size");
                            }
                            yield new MySqlHybridBinlogSplit(
                                    binlogSerializer.deserialize(binlogSerializer.getVersion(), in.readNBytes(length)),
                                    MySqlHybridBinlogSplit.Phase.values()[phase],
                                    high);
                        }
                        default -> throw new IOException("Unknown MySQL hybrid split marker");
                    };
            if (in.available() != 0) {
                throw new IOException("Trailing bytes in MySQL hybrid split state");
            }
            return split;
        } catch (RuntimeException error) {
            throw new IOException("Corrupt MySQL hybrid split state", error);
        }
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
