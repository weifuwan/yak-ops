package io.yak.ops.connector.jdbc.source.split;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.TableId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Serializes immutable JDBC split definitions for reader assignment and durable checkpoints.
 *
 * <p>Versioned bytes retain projected fields, inclusive range bounds, emitted-key cursor,
 * table identity and schema fingerprint, but never embed connection credentials.
 */
public final class JdbcSourceSplitSerializer implements SimpleVersionedSerializer<JdbcSourceSplit> {

    private static final int VERSION = 2;
    private static final int MAX_SPLIT_BYTES = 1_048_576;
    private static final int MAX_COLUMNS = 8_192;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(JdbcSourceSplit split) throws IOException {
        Objects.requireNonNull(split, "split");
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeUTF(split.splitId());
            writeNullableString(out, split.tableId().catalog());
            writeNullableString(out, split.tableId().schema());
            out.writeUTF(split.tableId().table());
            out.writeInt(split.columns().size());
            for (String column : split.columns()) {
                out.writeUTF(column);
            }
            writeNullableString(out, split.splitColumn());
            writeNullableLong(out, split.lowerBound());
            writeNullableLong(out, split.upperBound());
            writeNullableLong(out, split.lastEmittedKey());
            out.writeUTF(split.schemaFingerprint());
            out.flush();
            if (buffer.size() > MAX_SPLIT_BYTES) {
                throw new IOException("JDBC split state exceeds the maximum size");
            }
            return buffer.toByteArray();
        }
    }

    @Override
    public JdbcSourceSplit deserialize(int version, byte[] serialized) throws IOException {
        if (version != VERSION || serialized == null || serialized.length > MAX_SPLIT_BYTES) {
            throw new IOException("Unsupported JDBC split state version or length");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(serialized))) {
            String id = in.readUTF();
            String catalog = readNullableString(in);
            String schema = readNullableString(in);
            String table = in.readUTF();
            int count = in.readInt();
            if (count <= 0 || count > MAX_COLUMNS) {
                throw new IOException("Invalid JDBC split column count");
            }
            List<String> columns = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                columns.add(in.readUTF());
            }
            String splitColumn = readNullableString(in);
            Long lower = readNullableLong(in);
            Long upper = readNullableLong(in);
            Long position = readNullableLong(in);
            String schemaFingerprint = in.readUTF();
            if (in.available() != 0) {
                throw new IOException("Unexpected bytes after JDBC split state");
            }
            return new JdbcSourceSplit(
                    id,
                    new TableId(catalog, schema, table),
                    columns,
                    splitColumn,
                    lower,
                    upper,
                    position,
                    schemaFingerprint);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid JDBC split state", exception);
        }
    }

    private void writeNullableString(DataOutputStream out, String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeUTF(value);
        }
    }

    private String readNullableString(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private void writeNullableLong(DataOutputStream out, Long value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeLong(value);
        }
    }

    private Long readNullableLong(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readLong() : null;
    }
}
