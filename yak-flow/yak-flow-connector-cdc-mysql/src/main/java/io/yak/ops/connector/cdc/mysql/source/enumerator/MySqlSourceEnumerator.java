package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlBinlogSplitAssigner;
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
    private final MySqlBinlogSplitAssigner assigner;
    private boolean started;
    private boolean closed;

    public MySqlSourceEnumerator(
            SplitEnumeratorContext<MySqlBinlogSplit> context,
            String sourceFingerprint,
            MySqlPendingSplitsState restored) {
        this.context = Objects.requireNonNull(context, "context");
        if (context.currentParallelism() != 1) {
            throw new IllegalArgumentException("MySQL Binlog Source supports one reader");
        }
        assigner = new MySqlBinlogSplitAssigner(sourceFingerprint, restored);
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
        MySqlBinlogSplit next = assigner.getNext(subtaskId);
        if (next != null) {
            context.assignSplit(next, 0);
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
        if (closed) {
            throw new IllegalStateException("Unexpected returned MySQL Binlog split");
        }
        assigner.addSplitsBack(splits, subtaskId);
    }

    @Override
    public MySqlPendingSplitsState snapshotState(long checkpointId) {
        if (!started || closed || checkpointId < 0) {
            throw new IllegalStateException("MySQL Binlog enumerator cannot checkpoint");
        }
        return assigner.snapshotState(checkpointId);
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        assigner.notifyCheckpointComplete(checkpointId);
    }

    @Override
    public void close() {
        closed = true;
        assigner.close();
    }
}
