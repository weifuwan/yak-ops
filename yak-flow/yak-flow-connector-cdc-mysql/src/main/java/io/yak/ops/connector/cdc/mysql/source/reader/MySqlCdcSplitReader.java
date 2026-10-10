package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.source.reader.RecordsBySplits;
import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.debezium.MySqlBinlogEngine;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fetcher-owned Debezium Binlog I/O exposed through the shared Connector Base SplitReader.
 *
 * <p>A single unbounded split remains assigned until the Source task is cancelled.
 * The Debezium engine enqueues bounded detached changes; a fetch only transfers them.
 */
public final class MySqlCdcSplitReader implements SplitReader<BinlogEvent, MySqlBinlogSplit> {

    private final MySqlBinlogEngine engine;
    private final String fingerprint;
    private boolean assigned;
    private boolean closed;

    public MySqlCdcSplitReader(MySqlCdcSourceConfig config, String fingerprint) {
        engine = new MySqlBinlogEngine(config);
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    }

    @Override
    public void addSplits(List<MySqlBinlogSplit> splits) throws Exception {
        if (closed || assigned || splits.size() != 1) {
            throw new IllegalStateException("MySQL Binlog reader requires exactly one assigned split");
        }
        MySqlBinlogSplit split = Objects.requireNonNull(splits.getFirst(), "split");
        if (!fingerprint.equals(split.definitionFingerprint())) {
            throw new IllegalArgumentException("MySQL Binlog split is from another source definition");
        }
        engine.start(split);
        assigned = true;
    }

    @Override
    public RecordsWithSplitIds<BinlogEvent> fetch() throws Exception {
        if (closed || !assigned) {
            throw new IllegalStateException("MySQL Binlog reader has no live split");
        }
        BinlogEvent item = engine.poll();
        return new RecordsBySplits<>(item == null ? Map.of() : Map.of(MySqlBinlogSplit.ID, List.of(item)), Set.of());
    }

    @Override
    public void wakeUp() {
        // The bounded engine queue poll returns without interrupting the Binlog subscription.
    }

    @Override
    public void cancel() {
        engine.requestStop();
    }

    public byte[] snapshotHistory() {
        try {
            return engine.snapshotHistory();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to checkpoint MySQL CDC Schema History", exception);
        }
    }

    @Override
    public void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        engine.close();
    }
}
