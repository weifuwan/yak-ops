package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplitSerializer;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/** Checkpoints the Binlog split-assignment status without duplicating reader-owned offsets. */
public final class MySqlPendingSplitsStateSerializer implements SimpleVersionedSerializer<MySqlPendingSplitsState> {

    private static final int VERSION = 1;
    private final MySqlBinlogSplitSerializer splitSerializer = new MySqlBinlogSplitSerializer();

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(MySqlPendingSplitsState state) throws IOException {
        Objects.requireNonNull(state, "state");
        try (var buffer = new ByteArrayOutputStream();
                var out = new DataOutputStream(buffer)) {
            out.writeUTF(state.sourceFingerprint());
            out.writeBoolean(state.splitAssigned());
            if (!state.splitAssigned()) {
                byte[] block = splitSerializer.serialize(state.pendingSplit());
                out.writeInt(block.length);
                out.write(block);
            }
            out.flush();
            return buffer.toByteArray();
        }
    }

    @Override
    public MySqlPendingSplitsState deserialize(int version, byte[] serialized) throws IOException {
        if (version != VERSION || serialized == null || serialized.length > 8 * 1024 * 1024) {
            throw new IOException("Unsupported MySQL enumerator state");
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(serialized))) {
            String fingerprint = in.readUTF();
            boolean assigned = in.readBoolean();
            MySqlBinlogSplit pending = null;
            if (!assigned) {
                int length = in.readInt();
                if (length < 0 || length > in.available()) {
                    throw new IOException("Invalid MySQL pending split size");
                }
                pending = splitSerializer.deserialize(splitSerializer.getVersion(), in.readNBytes(length));
            }
            if (in.available() != 0) {
                throw new IOException("Trailing bytes in MySQL enumerator state");
            }
            return new MySqlPendingSplitsState(fingerprint, assigned, pending);
        } catch (RuntimeException error) {
            throw new IOException("Invalid MySQL enumerator state", error);
        }
    }
}
