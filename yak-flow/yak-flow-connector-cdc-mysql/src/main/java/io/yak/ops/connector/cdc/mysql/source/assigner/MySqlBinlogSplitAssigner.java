package io.yak.ops.connector.cdc.mysql.source.assigner;

import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import java.util.List;
import java.util.Objects;

/** Assigns the latest-offset Binlog split to the only replication reader. */
public final class MySqlBinlogSplitAssigner implements MySqlSplitAssigner<MySqlBinlogSplit, MySqlPendingSplitsState> {

    private final String sourceFingerprint;
    private MySqlBinlogSplit pending;
    private boolean assigned;

    public MySqlBinlogSplitAssigner(String sourceFingerprint, MySqlPendingSplitsState restored) {
        this.sourceFingerprint = Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        if (restored == null) {
            pending = new MySqlBinlogSplit(sourceFingerprint, null, new byte[0]);
        } else {
            if (!sourceFingerprint.equals(restored.sourceFingerprint())) {
                throw new IllegalArgumentException("MySQL CDC definition changed since its checkpoint");
            }
            pending = restored.pendingSplit();
            assigned = restored.splitAssigned();
        }
    }

    @Override
    public MySqlBinlogSplit getNext(int subtaskId) {
        if (subtaskId != 0) {
            return null;
        }
        MySqlBinlogSplit next = pending;
        if (next != null) {
            pending = null;
            assigned = true;
        }
        return next;
    }

    @Override
    public void addSplitsBack(List<MySqlBinlogSplit> splits, int subtaskId) {
        if (subtaskId != 0 || splits.size() != 1 || pending != null || !assigned) {
            throw new IllegalStateException("Unexpected returned MySQL Binlog split");
        }
        MySqlBinlogSplit returned = Objects.requireNonNull(splits.getFirst(), "split");
        if (!sourceFingerprint.equals(returned.definitionFingerprint())) {
            throw new IllegalArgumentException("Returned MySQL Binlog split has an incompatible source");
        }
        pending = returned;
        assigned = false;
    }

    @Override
    public MySqlPendingSplitsState snapshotState(long checkpointId) {
        if (checkpointId < 0) {
            throw new IllegalArgumentException("Checkpoint ID must not be negative");
        }
        return new MySqlPendingSplitsState(sourceFingerprint, assigned, pending);
    }

    @Override
    public boolean notifyCheckpointComplete(long checkpointId) {
        return false;
    }

    public void close() {
        pending = null;
    }
}
