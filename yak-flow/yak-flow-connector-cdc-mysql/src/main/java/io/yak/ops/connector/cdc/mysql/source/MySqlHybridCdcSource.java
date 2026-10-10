package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlHybridEnumeratorState;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlHybridEnumeratorStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlHybridSourceEnumerator;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlHybridSourceReader;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplitSerializer;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.data.TableRecord;
import java.util.Objects;

/**
 * Initial-snapshot MySQL CDC source with parallel JDBC chunks and deferred Binlog replay.
 *
 * <p>A single Debezium reader anchors the low watermark before any snapshot starts.
 * Snapshot splits can run on parallel Readers; after their downstream output is covered by
 * a completed checkpoint, the original Binlog stream replays from the low watermark.
 *
 * <p>This conservative global replay approach is at-least-once and is distinct from
 * Flink CDC's per-chunk Low/High watermark snapshot normalization.
 */
public final class MySqlHybridCdcSource implements Source<TableRecord, MySqlHybridSplit, MySqlHybridEnumeratorState> {

    private final MySqlCdcSourceConfig config;
    private final String fingerprint;
    private final int chunkSize;
    private final MySqlHybridSplitSerializer splitSerializer = new MySqlHybridSplitSerializer();
    private final MySqlHybridEnumeratorStateSerializer enumeratorSerializer =
            new MySqlHybridEnumeratorStateSerializer();

    MySqlHybridCdcSource(MySqlCdcSourceConfig config, String sourceFingerprint, int chunkSize) {
        this.config = Objects.requireNonNull(config, "config");
        if (chunkSize < 1 || chunkSize > 100_000) {
            throw new IllegalArgumentException("Hybrid snapshot chunk size must be between 1 and 100000");
        }
        this.chunkSize = chunkSize;
        fingerprint = Objects.requireNonNull(sourceFingerprint, "sourceFingerprint") + "-hybrid-" + chunkSize;
        config.schemas().values().forEach(io.yak.ops.connector.cdc.mysql.source.assigner.MySqlChunkSplitter::splitKey);
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public SplitEnumerator<MySqlHybridSplit, MySqlHybridEnumeratorState> createEnumerator(
            SplitEnumeratorContext<MySqlHybridSplit> context) {
        return new MySqlHybridSourceEnumerator(context, config, fingerprint, chunkSize, null);
    }

    @Override
    public SplitEnumerator<MySqlHybridSplit, MySqlHybridEnumeratorState> restoreEnumerator(
            SplitEnumeratorContext<MySqlHybridSplit> context, MySqlHybridEnumeratorState state) {
        return new MySqlHybridSourceEnumerator(
                context, config, fingerprint, chunkSize, Objects.requireNonNull(state, "checkpoint state"));
    }

    @Override
    public SourceReader<TableRecord, MySqlHybridSplit> createReader(SourceReaderContext context) {
        Objects.requireNonNull(context, "context");
        if (context.getConfiguration()
                .get(CheckpointingOptions.CHECKPOINTING_INTERVAL)
                .isZero()) {
            throw new IllegalArgumentException("MySQL Hybrid Snapshot requires periodic durable checkpoints");
        }
        return new MySqlHybridSourceReader(config, context);
    }

    @Override
    public SimpleVersionedSerializer<MySqlHybridSplit> getSplitSerializer() {
        return splitSerializer;
    }

    @Override
    public SimpleVersionedSerializer<MySqlHybridEnumeratorState> getEnumeratorCheckpointSerializer() {
        return enumeratorSerializer;
    }
}
