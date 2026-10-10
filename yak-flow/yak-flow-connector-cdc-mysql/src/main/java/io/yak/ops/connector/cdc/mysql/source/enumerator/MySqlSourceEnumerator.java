package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import java.util.List;
import java.util.Objects;

/**
 * Assigns exactly one unbounded Binlog split to a single registered SourceReader.
 *
 * <p>No JDBC or Debezium I/O runs on the coordinator event loop. The Runtime separately
 * checkpoints assigned split delivery and mailbox-emitted reader progress.
 */
public final class MySqlSourceEnumerator implements SplitEnumerator<MySqlBinlogSplit, MySqlPendingSplitsState> {

    private final SplitEnumeratorContext<MySqlBinlogSplit> context;
    private final String sourceFingerprint;
    private MySqlBinlogSplit pending;
    private boolean assigned;
    private boolean started;
    private boolean closed;

    public MySqlSourceEnumerator(
            SplitEnumeratorContext<MySqlBinlogSplit> context,
            String sourceFingerprint,
            MySqlPendingSplitsState restored) {
        this.context = Objects.requireNonNull(context, "context");
        this.sourceFingerprint = Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        if (context.currentParallelism() != 1) {
            throw new IllegalArgumentException("MySQL Binlog Source supports one reader in PR1");
        }
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
    public void start() {
        if (started || closed) {
            throw new IllegalStateException("MySQL SourceEnumerator cannot be started twice");
        }
        started = true;
    }

    @Override
    public void handleSplitRequest(int subtaskId) {
        if (!started || closed) {
            throw new IllegalStateException("MySQL SourceEnumerator is not running");
        }
        if (subtaskId != 0 || !context.registeredReaders().contains(0)) {
            throw new IllegalArgumentException("Unregistered MySQL Binlog reader");
        }
        if (pending != null) {
            context.assignSplit(pending, 0);
            pending = null;
            assigned = true;
        }
    }

    @Override
    public void addReader(int subtaskId) {
        if (subtaskId != 0) {
            throw new IllegalArgumentException("MySQL CDC only supports reader subtask zero");
        }
    }

    @Override
    public void addSplitsBack(List<MySqlBinlogSplit> splits, int subtaskId) {
        if (closed || subtaskId != 0 || splits.size() != 1 || pending != null) {
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
        if (!started || closed || checkpointId < 0) {
            throw new IllegalStateException("MySQL Binlog enumerator cannot checkpoint");
        }
        return new MySqlPendingSplitsState(sourceFingerprint, assigned, pending);
    }

    @Override
    public void close() {
        closed = true;
        pending = null;
    }
}
