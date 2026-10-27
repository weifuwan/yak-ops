package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlHybridEnumeratorState;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.TableRecord;
import java.util.Objects;

/**
 * Single MySQL CDC Source entry point for initial snapshot and latest-offset startup.
 *
 * <p>The underlying implementations retain their distinct split, reader and checkpoint types
 * until those contracts are unified. Delegation does not translate checkpoint state, change
 * fingerprints or introduce a second serializer.
 *
 * @param <SplitT> split type of the selected startup mode
 * @param <StateT> enumerator checkpoint type of the selected startup mode
 */
public final class MySqlSource<SplitT extends SourceSplit, StateT> implements Source<TableRecord, SplitT, StateT> {

    private final StartupMode startupMode;
    private final MySqlCdcSourceConfig config;
    private final int snapshotChunkSize;
    private final Source<TableRecord, SplitT, StateT> source;

    private MySqlSource(
            StartupMode startupMode,
            MySqlCdcSourceConfig config,
            int snapshotChunkSize,
            Source<TableRecord, SplitT, StateT> source) {
        this.startupMode = Objects.requireNonNull(startupMode, "startupMode");
        this.config = Objects.requireNonNull(config, "config");
        this.snapshotChunkSize = snapshotChunkSize;
        this.source = Objects.requireNonNull(source, "source");
    }

    public static MySqlCdcSourceBuilder builder() {
        return new MySqlCdcSourceBuilder();
    }

    static MySqlSource<?, ?> create(
            MySqlCdcSourceBuilder builder, StartupMode startupMode, int snapshotChunkSize, TableMetrics metrics) {
        Objects.requireNonNull(startupMode, "startupMode");
        Objects.requireNonNull(metrics, "metrics");
        // Match the existing Hybrid builder: its Binlog definition does not own task metrics.
        TableMetrics definitionMetrics = startupMode.includesSnapshot() ? new TableMetrics() : metrics;
        MySqlCdcSource binlog = Objects.requireNonNull(builder, "builder").build(definitionMetrics);
        if (startupMode == StartupMode.INITIAL) {
            MySqlHybridCdcSource hybrid = binlog.initialSnapshot(snapshotChunkSize, metrics);
            return new MySqlSource<MySqlHybridSplit, MySqlHybridEnumeratorState>(
                    startupMode, binlog.config(), snapshotChunkSize, hybrid);
        }
        return new MySqlSource<MySqlBinlogSplit, MySqlPendingSplitsState>(startupMode, binlog.config(), 0, binlog);
    }

    public StartupMode startupMode() {
        return startupMode;
    }

    public MySqlCdcSourceConfig config() {
        return config;
    }

    public int snapshotChunkSize() {
        if (!startupMode.includesSnapshot()) {
            throw new IllegalStateException("Latest-offset MySQL CDC does not use snapshot chunks");
        }
        return snapshotChunkSize;
    }

    // Used by connector tests while the mode-specific split types remain distinct.
    Source<TableRecord, SplitT, StateT> delegate() {
        return source;
    }

    @Override
    public Boundedness getBoundedness() {
        return source.getBoundedness();
    }

    @Override
    public SplitEnumerator<SplitT, StateT> createEnumerator(SplitEnumeratorContext<SplitT> context) throws Exception {
        return source.createEnumerator(context);
    }

    @Override
    public SplitEnumerator<SplitT, StateT> restoreEnumerator(
            SplitEnumeratorContext<SplitT> context, StateT checkpointState) throws Exception {
        return source.restoreEnumerator(context, checkpointState);
    }

    @Override
    public SourceReader<TableRecord, SplitT> createReader(SourceReaderContext context) throws Exception {
        return source.createReader(context);
    }

    @Override
    public SimpleVersionedSerializer<SplitT> getSplitSerializer() {
        return source.getSplitSerializer();
    }

    @Override
    public SimpleVersionedSerializer<StateT> getEnumeratorCheckpointSerializer() {
        return source.getEnumeratorCheckpointSerializer();
    }
}
