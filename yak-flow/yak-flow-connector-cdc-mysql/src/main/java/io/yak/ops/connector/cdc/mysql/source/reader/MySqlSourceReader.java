package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.source.reader.SingleThreadMultiplexSourceReaderBase;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.TableRecord;
import java.util.Map;

/**
 * Receives unbounded MySQL Binlog changes on the mailbox and checkpoints delivered offsets.
 *
 * <p>All Debezium I/O is owned by the background SplitReader and never touches the Runtime
 * state backend. This reader emits an entire UPDATE pair before advancing progress.
 */
public final class MySqlSourceReader
        extends SingleThreadMultiplexSourceReaderBase<BinlogEvent, TableRecord, MySqlBinlogSplit, MySqlCdcSplitState> {

    private final MySqlCdcSplitReader splitReader;

    public MySqlSourceReader(MySqlCdcSourceConfig config, String fingerprint, SourceReaderContext context) {
        this(new MySqlCdcSplitReader(config, fingerprint), context);
    }

    private MySqlSourceReader(MySqlCdcSplitReader splitReader, SourceReaderContext context) {
        super(() -> splitReader, new MySqlCdcRecordEmitter(), context);
        this.splitReader = splitReader;
    }

    @Override
    public void start() {
        context.sendSplitRequest();
    }

    @Override
    protected MySqlCdcSplitState initializedState(MySqlBinlogSplit split) {
        return new MySqlCdcSplitState(split);
    }

    @Override
    protected MySqlBinlogSplit toSplitType(String splitId, MySqlCdcSplitState state) {
        if (!MySqlBinlogSplit.ID.equals(splitId)) {
            throw new IllegalArgumentException("Unexpected MySQL Binlog split ID");
        }
        return state.checkpoint(splitReader.snapshotHistory());
    }

    @Override
    protected void onSplitFinished(Map<String, MySqlCdcSplitState> finished) {
        throw new IllegalStateException("A continuous MySQL Binlog split must not finish normally");
    }
}
