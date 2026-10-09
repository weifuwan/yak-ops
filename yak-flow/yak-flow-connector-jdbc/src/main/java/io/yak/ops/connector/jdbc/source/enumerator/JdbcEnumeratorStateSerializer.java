package io.yak.ops.connector.jdbc.source.enumerator;

import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplitSerializer;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Versioned snapshot of table planning progress and unassigned JDBC splits. */
public final class JdbcEnumeratorStateSerializer implements SimpleVersionedSerializer<JdbcEnumeratorState> {

    private static final int VERSION = 2;
    private static final int MAX_SPLITS = 100_000;
    private static final int MAX_SPLIT_SIZE = 1_048_576;
    private final JdbcSourceSplitSerializer splitSerializer = new JdbcSourceSplitSerializer();

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(JdbcEnumeratorState state) throws IOException {
        Objects.requireNonNull(state, "state");
        if (state.pendingSplits().size() > MAX_SPLITS) {
            throw new IOException("Too many JDBC splits in enumerator state");
        }
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeUTF(state.sourceFingerprint());
            out.writeInt(state.nextTableIndex());
            out.writeInt(state.pendingSplits().size());
            for (JdbcSourceSplit split : state.pendingSplits()) {
                byte[] serialized = splitSerializer.serialize(split);
                out.writeInt(serialized.length);
                out.write(serialized);
            }
            out.flush();
            return buffer.toByteArray();
        }
    }

    @Override
    public JdbcEnumeratorState deserialize(int version, byte[] serialized) throws IOException {
        if (version != VERSION || serialized == null) {
            throw new IOException("Unsupported JDBC enumerator state version");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(serialized))) {
            String fingerprint = in.readUTF();
            int nextTableIndex = in.readInt();
            int splitCount = in.readInt();
            if (splitCount < 0 || splitCount > MAX_SPLITS) {
                throw new IOException("Invalid JDBC enumerator split count");
            }
            List<JdbcSourceSplit> pending = new ArrayList<>(splitCount);
            for (int index = 0; index < splitCount; index++) {
                int length = in.readInt();
                if (length < 0 || length > MAX_SPLIT_SIZE || length > in.available()) {
                    throw new IOException("Invalid JDBC split block size");
                }
                pending.add(splitSerializer.deserialize(splitSerializer.getVersion(), in.readNBytes(length)));
            }
            if (in.available() != 0) {
                throw new IOException("Unexpected bytes after JDBC enumerator state");
            }
            return new JdbcEnumeratorState(fingerprint, nextTableIndex, pending);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid JDBC enumerator state", exception);
        }
    }
}
