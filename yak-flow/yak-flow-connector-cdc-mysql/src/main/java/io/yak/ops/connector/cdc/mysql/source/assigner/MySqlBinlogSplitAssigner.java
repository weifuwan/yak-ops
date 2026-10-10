package io.yak.ops.connector.cdc.mysql.source.assigner;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import java.util.Objects;

/** Assigns one immutable Binlog split to the sole replication reader (subtask zero). */
public final class MySqlBinlogSplitAssigner {

    private MySqlHybridBinlogSplit pending;
    private boolean assigned;

    public MySqlBinlogSplitAssigner(String fingerprint) {
        pending = new MySqlHybridBinlogSplit(
                new MySqlBinlogSplit(Objects.requireNonNull(fingerprint, "fingerprint"), null, new byte[0]),
                MySqlHybridBinlogSplit.Phase.BOOTSTRAP,
                null);
    }

    public MySqlBinlogSplitAssigner(MySqlHybridBinlogSplit pending, boolean assigned) {
        if (assigned == (pending != null)) {
            throw new IllegalArgumentException("Binlog assignment and pending state conflict");
        }
        this.pending = pending;
        this.assigned = assigned;
    }

    public MySqlHybridBinlogSplit next(int subtaskId) {
        if (subtaskId != 0) {
            return null;
        }
        MySqlHybridBinlogSplit result = pending;
        if (result != null) {
            pending = null;
            assigned = true;
        }
        return result;
    }

    public void addBack(MySqlHybridBinlogSplit split) {
        if (pending != null || !assigned) {
            throw new IllegalStateException("Binlog is not assigned");
        }
        pending = Objects.requireNonNull(split, "split");
        assigned = false;
    }

    public boolean assigned() {
        return assigned;
    }

    public MySqlHybridBinlogSplit pending() {
        return pending;
    }
}
