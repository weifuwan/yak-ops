package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persists hybrid coordinator work and the checkpoint-gated snapshot-to-Binlog phase.
 *
 * <p>Reader-emitted progress is not duplicated here; completed split IDs are used only
 * for assignment accounting. The state format includes no credentials or active I/O.
 */
public final class MySqlHybridEnumeratorStateSerializer
        implements SimpleVersionedSerializer<MySqlHybridEnumeratorState> {

    private static final int VERSION = 1;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SPLITS = 10_000;
    private final MySqlHybridSplitSerializer splitSerializer = new MySqlHybridSplitSerializer();

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(MySqlHybridEnumeratorState state) throws IOException {
        try (var bytes = new ByteArrayOutputStream();
                var out = new DataOutputStream(bytes)) {
            out.writeUTF(state.fingerprint());
            out.writeByte(state.phase().ordinal());
            out.writeBoolean(state.binlogAssigned());
            if (state.pendingBinlog() != null) {
                writeSplit(out, state.pendingBinlog());
            }
            writeOffset(out, state.lowWatermark());
            writeOffset(out, state.highWatermark());
            out.writeBoolean(state.snapshotPlanned());
            out.writeInt(state.totalSnapshotSplits());
            writeSplits(out, state.pendingSnapshots());
            out.writeInt(state.assignedSnapshots().size());
            for (var entry : state.assignedSnapshots().entrySet()) {
                out.writeUTF(entry.getKey());
                writeSplit(out, entry.getValue());
            }
            out.writeInt(state.finishedSnapshots().size());
            for (String id : state.finishedSnapshots()) {
                out.writeUTF(id);
            }
            out.flush();
            if (bytes.size() > MAX_BYTES) {
                throw new IOException("MySQL hybrid enumerator checkpoint exceeds size limit");
            }
            return bytes.toByteArray();
        }
    }

    @Override
    public MySqlHybridEnumeratorState deserialize(int version, byte[] bytes) throws IOException {
        if (version != VERSION || bytes == null || bytes.length > MAX_BYTES) {
            throw new IOException("Unsupported MySQL hybrid enumerator checkpoint");
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String fingerprint = in.readUTF();
            int phaseIndex = in.readUnsignedByte();
            if (phaseIndex >= MySqlHybridEnumeratorState.Phase.values().length) {
                throw new IOException("Unknown MySQL hybrid phase");
            }
            boolean binlogAssigned = in.readBoolean();
            MySqlHybridBinlogSplit pending = binlogAssigned ? null : (MySqlHybridBinlogSplit) readSplit(in);
            BinlogOffset low = readOffset(in);
            BinlogOffset high = readOffset(in);
            boolean planned = in.readBoolean();
            int total = in.readInt();
            List<MySqlSnapshotSplit> pendingSnapshots = readSplits(in);
            int assignedCount = boundedCount(in);
            Map<String, MySqlSnapshotSplit> assigned = new LinkedHashMap<>();
            for (int i = 0; i < assignedCount; i++) {
                String id = in.readUTF();
                MySqlSnapshotSplit split = (MySqlSnapshotSplit) readSplit(in);
                if (!id.equals(split.splitId()) || assigned.putIfAbsent(id, split) != null) {
                    throw new IOException("Corrupt assigned MySQL snapshot split");
                }
            }
            int finishedCount = boundedCount(in);
            Set<String> finished = new LinkedHashSet<>();
            for (int i = 0; i < finishedCount; i++) {
                if (!finished.add(in.readUTF())) {
                    throw new IOException("Duplicate finished MySQL snapshot split");
                }
            }
            if (in.available() != 0) {
                throw new IOException("Unexpected bytes after MySQL hybrid enumerator state");
            }
            return new MySqlHybridEnumeratorState(
                    fingerprint,
                    MySqlHybridEnumeratorState.Phase.values()[phaseIndex],
                    pending,
                    binlogAssigned,
                    low,
                    high,
                    planned,
                    total,
                    pendingSnapshots,
                    assigned,
                    finished);
        } catch (RuntimeException error) {
            throw new IOException("Corrupt MySQL hybrid enumerator state", error);
        }
    }

    private void writeSplits(DataOutputStream out, List<MySqlSnapshotSplit> splits) throws IOException {
        if (splits.size() > MAX_SPLITS) {
            throw new IOException("Too many MySQL snapshot splits");
        }
        out.writeInt(splits.size());
        for (MySqlSnapshotSplit split : splits) {
            writeSplit(out, split);
        }
    }

    private List<MySqlSnapshotSplit> readSplits(DataInputStream in) throws IOException {
        int count = boundedCount(in);
        List<MySqlSnapshotSplit> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add((MySqlSnapshotSplit) readSplit(in));
        }
        return result;
    }

    private void writeSplit(DataOutputStream out, MySqlHybridSplit split) throws IOException {
        byte[] bytes = splitSerializer.serialize(split);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private MySqlHybridSplit readSplit(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_BYTES || length > in.available()) {
            throw new IOException("Invalid MySQL split length");
        }
        return splitSerializer.deserialize(splitSerializer.getVersion(), in.readNBytes(length));
    }

    private static int boundedCount(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_SPLITS) {
            throw new IOException("Invalid MySQL split count");
        }
        return count;
    }

    private static void writeOffset(DataOutputStream out, BinlogOffset offset) throws IOException {
        out.writeBoolean(offset != null);
        if (offset != null) {
            offset.writeTo(out);
        }
    }

    private static BinlogOffset readOffset(DataInputStream in) throws IOException {
        return in.readBoolean() ? BinlogOffset.readFrom(in) : null;
    }
}
