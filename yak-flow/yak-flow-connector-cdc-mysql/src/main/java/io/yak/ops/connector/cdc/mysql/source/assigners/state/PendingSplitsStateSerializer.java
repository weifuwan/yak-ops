package io.yak.ops.connector.cdc.mysql.source.assigners.state;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.AssignerStatus;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.split.ChunkRangeStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.TableId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32C;

/**
 * Serializes enumerator state in one versioned envelope shared by the MySQL split assigners.
 *
 * <p>The split serializer version and the kind of pending splits state are included in the
 * envelope, as in Flink CDC. Reader-owned binlog offsets are not duplicated in the
 * assigned-binlog state. A binlog split returned to the enumerator remains checkpointed
 * while pending, so an interrupted latest-offset reader cannot accidentally seek to NOW.
 *
 * <p>Version 2 aligns Flink CDC's snapshot-first hybrid state layout and state flags.
 * The trailing early-Binlog handoff fields and Debezium 2.7/typed Chunk payload are
 * YakFlow-specific, so this is not Flink checkpoint binary compatibility.
 * Earlier pre-release versions are intentionally unsupported.
 */
public final class PendingSplitsStateSerializer implements SimpleVersionedSerializer<PendingSplitsState> {
    private static final int VERSION = 4;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int MAX_SPLITS = 10_000;
    // Retain Flink CDC 3.7's discriminator values for all three pending-state kinds.
    private static final int SNAPSHOT_PENDING_SPLITS_STATE_FLAG = 1;
    private static final int BINLOG_PENDING_SPLITS_STATE_FLAG = 2;
    private static final int HYBRID_PENDING_SPLITS_STATE_FLAG = 3;

    private final SimpleVersionedSerializer<MySqlSplit> splitSerializer;

    public PendingSplitsStateSerializer(SimpleVersionedSerializer<MySqlSplit> splitSerializer) {
        this.splitSerializer = Objects.requireNonNull(splitSerializer, "splitSerializer");
    }

    public PendingSplitsStateSerializer() {
        this(MySqlSplitSerializer.INSTANCE);
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(PendingSplitsState state) throws IOException {
        Objects.requireNonNull(state, "state");
        if (state.serializedFormCache != null) {
            return state.serializedFormCache.clone();
        }
        try (var buffer = new ByteArrayOutputStream();
                var out = new DataOutputStream(buffer)) {
            out.writeInt(splitSerializer.getVersion());
            if (state instanceof SnapshotPendingSplitsState snapshot) {
                out.writeInt(SNAPSHOT_PENDING_SPLITS_STATE_FLAG);
                serializeSnapshotPendingSplitsState(snapshot, out);
            } else if (state instanceof BinlogPendingSplitsState binlog) {
                out.writeInt(BINLOG_PENDING_SPLITS_STATE_FLAG);
                serializeBinlogPendingSplitsState(binlog, out);
            } else if (state instanceof HybridPendingSplitsState hybrid) {
                out.writeInt(HYBRID_PENDING_SPLITS_STATE_FLAG);
                serializeHybridPendingSplitsState(hybrid, out);
            } else {
                throw new IOException(
                        "Unsupported pending splits state: " + state.getClass().getName());
            }
            out.flush();
            // Protect the state kind, split format and Snapshot progress with CRC32C.
            if (buffer.size() > MAX_BYTES - Integer.BYTES) {
                throw new IOException("Oversized MySQL enumerator checkpoint");
            }
            byte[] body = buffer.toByteArray();
            CRC32C checksum = new CRC32C();
            checksum.update(body, 0, body.length);
            out.writeInt((int) checksum.getValue());
            out.flush();
            byte[] serialized = buffer.toByteArray();
            state.serializedFormCache = serialized;
            return serialized.clone();
        }
    }

    @Override
    public PendingSplitsState deserialize(int version, byte[] bytes) throws IOException {
        if (version != VERSION || bytes == null || bytes.length < Integer.BYTES * 2 || bytes.length > MAX_BYTES) {
            throw new IOException("Unsupported MySQL pending splits checkpoint format: " + version);
        }
        if (bytes.length < Integer.BYTES * 3) {
            throw new IOException("Truncated MySQL pending splits checkpoint");
        }
        int payloadLength = bytes.length - Integer.BYTES;
        int expectedChecksum =
                ByteBuffer.wrap(bytes, payloadLength, Integer.BYTES).getInt();
        CRC32C checksum = new CRC32C();
        checksum.update(bytes, 0, payloadLength);
        if ((int) checksum.getValue() != expectedChecksum) {
            throw new IOException("MySQL pending splits checkpoint checksum mismatch");
        }
        bytes = Arrays.copyOf(bytes, payloadLength);
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int splitVersion = in.readInt();
            if (splitVersion != splitSerializer.getVersion()) {
                throw new IOException("Unsupported nested MySQL split serializer version: " + splitVersion);
            }
            int kind = in.readInt();
            PendingSplitsState state =
                    switch (kind) {
                        case SNAPSHOT_PENDING_SPLITS_STATE_FLAG ->
                            deserializeSnapshotPendingSplitsState(in, splitVersion);
                        case BINLOG_PENDING_SPLITS_STATE_FLAG -> deserializeBinlogPendingSplitsState(in, splitVersion);
                        case HYBRID_PENDING_SPLITS_STATE_FLAG -> deserializeHybridPendingSplitsState(in, splitVersion);
                        default -> throw new IOException("Unknown MySQL pending splits state kind: " + kind);
                    };
            if (in.available() != 0) {
                throw new IOException("Trailing bytes in MySQL pending splits state");
            }
            return state;
        } catch (RuntimeException error) {
            throw new IOException("Corrupted MySQL enumerator checkpoint", error);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Serialize
    // ------------------------------------------------------------------------------------------

    private void serializeSnapshotPendingSplitsState(SnapshotPendingSplitsState state, DataOutputStream out)
            throws IOException {
        // Flink CDC places the Snapshot state before the Hybrid Binlog assignment flag.
        writeTableIds(out, state.getAlreadyProcessedTables());
        writeRemainingSplits(out, state.getRemainingSplits());
        writeAssignedSnapshotSplits(out, state.getAssignedSplits());
        // YakFlow's completed fence preserves typed chunk boundaries and Debezium 2.7 offsets.
        MySqlSplitSerializer.writeFinishedSplits(out, state.getFinishedSplitInfos());
        out.writeInt(state.getSnapshotAssignerStatus().getStatusCode());
        writeTableIds(out, state.getRemainingTables());
        out.writeBoolean(state.isTableIdCaseSensitive());
        out.writeBoolean(state.isRemainingTablesCheckpointed());
        out.writeBoolean(state.isSnapshotMetaReleased());
        ChunkRangeStateSerializer.write(out, state.nextChunkRange());
        out.writeInt(state.nextChunkIndex());
        out.writeInt(state.nextTableIndex());
        out.writeBoolean(state.snapshotPlanned());
        out.writeInt(state.totalSnapshotSplits());
    }

    private void serializeHybridPendingSplitsState(HybridPendingSplitsState state, DataOutputStream out)
            throws IOException {
        serializeSnapshotPendingSplitsState(state.getSnapshotPendingSplits(), out);
        out.writeBoolean(state.isBinlogSplitAssigned());
        // YakFlow starts Binlog ahead of Snapshot, unlike Flink's deferred handoff.
        // Keep the durable early-Binlog anchor and phase as a trailing extension.
        out.writeByte(state.phase().ordinal());
        // Snapshot-first INITIAL has no pending Binlog until Snapshot finishes.
        out.writeBoolean(state.pendingBinlog() != null);
        if (state.pendingBinlog() != null) {
            writeMySqlSplit(state.pendingBinlog(), out);
        }
        writeOffset(out, state.lowWatermark());
        writeOffset(out, state.highWatermark());
        // The selected history is a functional prerequisite of the deferred
        // initial Binlog split. It must be durable while Snapshot is unfinished.
        writeOffset(out, state.schemaHistoryWatermark());
        byte[] history = state.schemaHistory();
        if (history.length > MAX_BYTES - 4096) {
            throw new IOException("Selected Snapshot schema history exceeds enumerator checkpoint limit");
        }
        out.writeInt(history.length);
        out.write(history);
    }

    private void serializeBinlogPendingSplitsState(BinlogPendingSplitsState state, DataOutputStream out)
            throws IOException {
        out.writeBoolean(state.splitAssigned());
        if (!state.splitAssigned()) {
            writeMySqlSplit(state.pendingSplit(), out);
        }
    }

    private void writeAssignedSnapshotSplits(DataOutputStream out, Map<String, MySqlSnapshotSplit> assignedSplits)
            throws IOException {
        if (assignedSplits.size() > MAX_SPLITS) {
            throw new IOException("Too many assigned MySQL snapshot splits");
        }
        out.writeInt(assignedSplits.size());
        for (Map.Entry<String, MySqlSnapshotSplit> entry : assignedSplits.entrySet()) {
            out.writeUTF(entry.getKey());
            writeMySqlSplit(entry.getValue(), out);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Deserialize
    // ------------------------------------------------------------------------------------------

    private SnapshotPendingSplitsState deserializeSnapshotPendingSplitsState(DataInputStream in, int splitVersion)
            throws IOException {
        List<TableId> alreadyProcessedTables = readTableIds(in);
        List<MySqlSnapshotSplit> remainingSplits = readMySqlSnapshotSplits(in, splitVersion);
        Map<String, MySqlSnapshotSplit> assignedSplits = readAssignedSnapshotSplits(in, splitVersion);
        var finishedInfos = MySqlSplitSerializer.readFinishedSplits(in);
        Set<String> finishedIds = new LinkedHashSet<>();
        for (var info : finishedInfos) {
            if (!finishedIds.add(info.splitId())) {
                throw new IOException("Duplicate finished MySQL snapshot split");
            }
        }
        AssignerStatus status;
        try {
            status = AssignerStatus.fromStatusCode(in.readInt());
        } catch (IllegalStateException invalidStatus) {
            throw new IOException("Unknown MySQL Snapshot Assigner status", invalidStatus);
        }
        List<TableId> remainingTables = readTableIds(in);
        boolean tableIdCaseSensitive = in.readBoolean();
        boolean remainingTablesCheckpointed = in.readBoolean();
        boolean snapshotMetaReleased = in.readBoolean();
        ChunkRange nextChunkRange = ChunkRangeStateSerializer.read(in);
        int nextChunkIndex = in.readInt();
        int nextTableIndex = in.readInt();
        boolean snapshotPlanned = in.readBoolean();
        int totalSnapshotSplits = in.readInt();
        return new SnapshotPendingSplitsState(
                snapshotPlanned,
                totalSnapshotSplits,
                remainingSplits,
                assignedSplits,
                finishedIds,
                finishedInfos,
                nextTableIndex,
                nextChunkIndex,
                nextChunkRange,
                alreadyProcessedTables,
                remainingTables,
                status,
                tableIdCaseSensitive,
                remainingTablesCheckpointed,
                snapshotMetaReleased);
    }

    private HybridPendingSplitsState deserializeHybridPendingSplitsState(DataInputStream in, int splitVersion)
            throws IOException {
        SnapshotPendingSplitsState snapshot = deserializeSnapshotPendingSplitsState(in, splitVersion);
        boolean binlogAssigned = in.readBoolean();
        int phaseIndex = in.readUnsignedByte();
        if (phaseIndex >= HybridPendingSplitsState.Phase.values().length) {
            throw new IOException("Unknown MySQL hybrid phase");
        }
        boolean hasPendingBinlog = in.readBoolean();
        if (hasPendingBinlog && binlogAssigned) {
            throw new IOException("Assigned Binlog cannot also be pending");
        }
        MySqlBinlogSplit pending =
                hasPendingBinlog ? readMySqlSplit(in, splitVersion).asBinlogSplit() : null;
        DebeziumOffset low = readOffset(in);
        DebeziumOffset high = readOffset(in);
        DebeziumOffset historyWatermark = readOffset(in);
        int historyLength = in.readInt();
        if (historyLength < 0 || historyLength > in.available() || historyLength > MAX_BYTES - 4096) {
            throw new IOException("Invalid selected Snapshot schema history");
        }
        byte[] history = in.readNBytes(historyLength);
        return new HybridPendingSplitsState(
                snapshot,
                binlogAssigned,
                pending,
                HybridPendingSplitsState.Phase.values()[phaseIndex],
                low,
                high,
                historyWatermark,
                history);
    }

    private BinlogPendingSplitsState deserializeBinlogPendingSplitsState(DataInputStream in, int splitVersion)
            throws IOException {
        boolean assigned = in.readBoolean();
        MySqlBinlogSplit pending =
                assigned ? null : readMySqlSplit(in, splitVersion).asBinlogSplit();
        return new BinlogPendingSplitsState(assigned, pending);
    }

    private LinkedHashMap<String, MySqlSnapshotSplit> readAssignedSnapshotSplits(DataInputStream in, int splitVersion)
            throws IOException {
        int count = boundedCount(in);
        LinkedHashMap<String, MySqlSnapshotSplit> assigned = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String id = in.readUTF();
            MySqlSnapshotSplit split = readMySqlSplit(in, splitVersion).asSnapshotSplit();
            if (!id.equals(split.splitId()) || assigned.putIfAbsent(id, split) != null) {
                throw new IOException("Corrupt assigned MySQL snapshot split");
            }
        }
        return assigned;
    }

    private void writeRemainingSplits(DataOutputStream out, List<MySqlSnapshotSplit> splits) throws IOException {
        if (splits.size() > MAX_SPLITS) {
            throw new IOException("Too many MySQL snapshot splits");
        }
        out.writeInt(splits.size());
        for (MySqlSnapshotSplit split : splits) {
            writeMySqlSplit(split, out);
        }
    }

    private List<MySqlSnapshotSplit> readMySqlSnapshotSplits(DataInputStream in, int splitVersion) throws IOException {
        int count = boundedCount(in);
        List<MySqlSnapshotSplit> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(readMySqlSplit(in, splitVersion).asSnapshotSplit());
        }
        return result;
    }

    private void writeMySqlSplit(MySqlSplit split, DataOutputStream out) throws IOException {
        byte[] bytes = splitSerializer.serialize(split);
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IOException("Invalid nested MySQL split size");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private MySqlSplit readMySqlSplit(DataInputStream in, int splitVersion) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_BYTES || length > in.available()) {
            throw new IOException("Invalid nested MySQL split length");
        }
        return splitSerializer.deserialize(splitVersion, in.readNBytes(length));
    }

    private static int boundedCount(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_SPLITS) {
            throw new IOException("Invalid MySQL split count");
        }
        return count;
    }

    private static void writeTableIds(DataOutputStream out, List<TableId> tables) throws IOException {
        if (tables.size() > MAX_SPLITS) throw new IOException("Too many MySQL Snapshot tables in checkpoint");
        out.writeInt(tables.size());
        for (TableId table : tables) {
            writeNullableString(out, table.catalog());
            writeNullableString(out, table.schema());
            out.writeUTF(table.table());
        }
    }

    private static List<TableId> readTableIds(DataInputStream in) throws IOException {
        int count = boundedCount(in);
        List<TableId> tables = new ArrayList<>(count);
        Set<TableId> unique = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            TableId table = new TableId(readNullableString(in), readNullableString(in), in.readUTF());
            if (!unique.add(table)) throw new IOException("Duplicate table in MySQL Snapshot checkpoint");
            tables.add(table);
        }
        return List.copyOf(tables);
    }

    private static void writeNullableString(DataOutputStream out, String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) out.writeUTF(value);
    }

    private static String readNullableString(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static void writeOffset(DataOutputStream out, DebeziumOffset offset) throws IOException {
        out.writeBoolean(offset != null);
        if (offset != null) {
            offset.writeTo(out);
        }
    }

    private static DebeziumOffset readOffset(DataInputStream in) throws IOException {
        return in.readBoolean() ? DebeziumOffset.readFrom(in) : null;
    }
}
