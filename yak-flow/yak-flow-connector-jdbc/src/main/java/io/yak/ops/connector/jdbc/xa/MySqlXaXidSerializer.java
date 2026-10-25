package io.yak.ops.connector.jdbc.xa;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import javax.transaction.xa.Xid;

/** Versioned durable checkpoint serializer; XA connections and database credentials are never saved. */
public final class MySqlXaXidSerializer implements SimpleVersionedSerializer<MySqlXaXid> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public byte[] serialize(MySqlXaXid xid) throws IOException {
        Objects.requireNonNull(xid, "xid");
        byte[] global = xid.getGlobalTransactionId();
        byte[] branch = xid.getBranchQualifier();
        try (var bytes = new ByteArrayOutputStream();
                var output = new DataOutputStream(bytes)) {
            output.writeInt(xid.getFormatId());
            output.writeInt(global.length);
            output.write(global);
            output.writeInt(branch.length);
            output.write(branch);
            output.flush();
            return bytes.toByteArray();
        }
    }

    @Override
    public MySqlXaXid deserialize(int version, byte[] serialized) throws IOException {
        if (version != getVersion() || serialized == null) {
            throw new IOException("Unsupported MySQL XA checkpoint state version");
        }
        try (var input = new DataInputStream(new ByteArrayInputStream(serialized))) {
            int format = input.readInt();
            int globalLength = input.readInt();
            if (format != MySqlXaXid.FORMAT_ID || globalLength < 1 || globalLength > Xid.MAXGTRIDSIZE) {
                throw new IOException("Invalid MySQL XA global transaction ID");
            }
            byte[] global = input.readNBytes(globalLength);
            int branchLength = input.readInt();
            if (global.length != globalLength || branchLength < 1 || branchLength > Xid.MAXBQUALSIZE) {
                throw new IOException("Invalid MySQL XA branch qualifier");
            }
            byte[] branch = input.readNBytes(branchLength);
            if (branch.length != branchLength || input.available() != 0) {
                throw new IOException("Truncated or trailing MySQL XA transaction state");
            }
            return new MySqlXaXid(global, branch);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid MySQL XA transaction identity", error);
        }
    }
}
