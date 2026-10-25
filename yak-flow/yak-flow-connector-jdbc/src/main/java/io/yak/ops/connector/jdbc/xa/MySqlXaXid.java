package io.yak.ops.connector.jdbc.xa;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import javax.transaction.xa.Xid;

/**
 * MySQL XA transaction identity. Each new attempt uses a fresh random branch nonce to avoid
 * collisions with abandoned prepared transactions from earlier attempts.
 *
 * <p>Global IDs are scoped to one persistent task instance and can be recognized during
 * XA RECOVER. Never roll back an XID owned by another task instance or another application.
 */
public final class MySqlXaXid implements Xid {

    public static final int FORMAT_ID = 0x59414B58;
    private static final byte[] PREFIX = "yakflow-xa:".getBytes(StandardCharsets.US_ASCII);
    private final byte[] global;
    private final byte[] branch;

    public MySqlXaXid(byte[] global, byte[] branch) {
        this.global = Objects.requireNonNull(global, "global").clone();
        this.branch = Objects.requireNonNull(branch, "branch").clone();
        if (this.global.length <= PREFIX.length
                || this.global.length > Xid.MAXGTRIDSIZE
                || this.branch.length == 0
                || this.branch.length > Xid.MAXBQUALSIZE
                || !Arrays.equals(Arrays.copyOf(this.global, PREFIX.length), PREFIX)) {
            throw new IllegalArgumentException("Invalid YakFlow MySQL XA transaction identifier");
        }
    }

    public static MySqlXaXid next(String instanceId, int subtask, long checkpointId) {
        if (instanceId == null || !instanceId.matches("[A-Za-z0-9_-]{1,40}") || subtask < 0 || checkpointId <= 0) {
            throw new IllegalArgumentException("Invalid persistent XA instance or checkpoint identity");
        }
        byte[] owner = instanceId.getBytes(StandardCharsets.US_ASCII);
        byte[] global = ByteBuffer.allocate(PREFIX.length + owner.length)
                .put(PREFIX)
                .put(owner)
                .array();
        UUID nonce = UUID.randomUUID();
        byte[] branch = ByteBuffer.allocate(28)
                .putLong(checkpointId)
                .putInt(subtask)
                .putLong(nonce.getMostSignificantBits())
                .putLong(nonce.getLeastSignificantBits())
                .array();
        return new MySqlXaXid(global, branch);
    }

    public boolean belongsTo(String instanceId) {
        return Arrays.equals(
                global,
                (new String(PREFIX, StandardCharsets.US_ASCII) + instanceId).getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public int getFormatId() {
        return FORMAT_ID;
    }

    @Override
    public byte[] getGlobalTransactionId() {
        return global.clone();
    }

    @Override
    public byte[] getBranchQualifier() {
        return branch.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof MySqlXaXid id
                        && Arrays.equals(global, id.global)
                        && Arrays.equals(branch, id.branch);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(global) + Arrays.hashCode(branch);
    }

    @Override
    public String toString() {
        return "MySqlXaXid{identity=redacted}";
    }
}
