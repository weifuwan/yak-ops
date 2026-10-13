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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Fetcher-owned Debezium Binlog I/O exposed through the shared Connector Base SplitReader.
 *
 * <p>A single unbounded split remains assigned until the Source task is cancelled.
 * The Debezium engine enqueues bounded detached changes; a fetch only transfers them.
 */
public final class MySqlCdcSplitReader implements SplitReader<BinlogEvent, MySqlBinlogSplit> {

    private static final Logger LOG = LoggerFactory.getLogger(MySqlCdcSplitReader.class);

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
        long pollStartedNanos = System.nanoTime();
        BinlogEvent item = engine.poll();
        if (item != null && "true".equals(MDC.get("taskLogDebug"))) {
            LOG.debug(
                    "CDC binlog batch fetched, events={}, pollMs={}",
                    item.records().size(),
                    (System.nanoTime() - pollStartedNanos) / 1_000_000);
        }
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
