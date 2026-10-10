package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Versioned, credential-free codec for the Binlog resume cursor and Debezium schema history.
 *
 * <p>Restored bytes are size bounded and never carry the MySQL password or active engine.
 */
public final class MySqlBinlogSplitSerializer implements SimpleVersionedSerializer<MySqlBinlogSplit> {

    private static final int VERSION = 1;
    private static final int MAX_STATE_BYTES = 8 * 1024 * 1024;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(MySqlBinlogSplit split) throws IOException {
        Objects.requireNonNull(split, "split");
        try (var bytes = new ByteArrayOutputStream();
                var out = new DataOutputStream(bytes)) {
            out.writeUTF(split.definitionFingerprint());
            out.writeBoolean(split.offset() != null);
            if (split.offset() != null) {
                split.offset().writeTo(out);
            }
            byte[] history = split.schemaHistory();
            if (history.length > MAX_STATE_BYTES) {
                throw new IOException("MySQL Schema History checkpoint exceeds size limit");
            }
            out.writeInt(history.length);
            out.write(history);
            out.flush();
            if (bytes.size() > MAX_STATE_BYTES) {
                throw new IOException("MySQL Binlog split exceeds checkpoint size limit");
            }
            return bytes.toByteArray();
        }
    }

    @Override
    public MySqlBinlogSplit deserialize(int version, byte[] serialized) throws IOException {
        if (version != VERSION || serialized == null || serialized.length > MAX_STATE_BYTES) {
            throw new IOException("Unsupported MySQL Binlog split version or length");
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(serialized))) {
            String fingerprint = in.readUTF();
            BinlogOffset offset = in.readBoolean() ? BinlogOffset.readFrom(in) : null;
            int count = in.readInt();
            if (count < 0 || count > in.available() || count > MAX_STATE_BYTES) {
                throw new IOException("Invalid MySQL Schema History block length");
            }
            byte[] history = in.readNBytes(count);
            if (in.available() != 0) {
                throw new IOException("Trailing bytes in MySQL Binlog split");
            }
            if (offset != null && history.length == 0) {
                throw new IOException("Restored Binlog offset is missing required Schema History");
            }
            return new MySqlBinlogSplit(fingerprint, offset, history);
        } catch (RuntimeException failure) {
            throw new IOException("Invalid MySQL Binlog split", failure);
        }
    }
}
