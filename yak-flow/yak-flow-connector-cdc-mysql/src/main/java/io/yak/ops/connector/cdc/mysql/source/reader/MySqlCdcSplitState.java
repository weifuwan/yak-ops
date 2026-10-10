package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import java.util.Objects;

/**
 * Mailbox-owned Binlog progress that changes only after an entire event has been delivered.
 *
 * <p>Debezium's internal prefetch or periodic offset writes never alter this state.
 */
public final class MySqlCdcSplitState {

    private final MySqlBinlogSplit assigned;
    private BinlogOffset emittedOffset;

    public MySqlCdcSplitState(MySqlBinlogSplit assigned) {
        this.assigned = Objects.requireNonNull(assigned, "assigned");
        emittedOffset = assigned.offset();
    }

    public void onEmitted(BinlogOffset offset) {
        emittedOffset = Objects.requireNonNull(offset, "offset");
    }

    public MySqlBinlogSplit checkpoint(byte[] history) {
        if (emittedOffset == null || history == null || history.length == 0) {
            throw new IllegalStateException("MySQL Binlog has no complete offset/schema-history checkpoint yet");
        }
        return assigned.withProgress(emittedOffset, history);
    }
}
