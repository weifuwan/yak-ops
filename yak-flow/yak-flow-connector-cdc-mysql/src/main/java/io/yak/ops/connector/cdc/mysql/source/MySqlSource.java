package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfigFactory;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlSourceEnumerator;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlSourceReader;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.connector.cdc.mysql.table.StartupMode;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
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
 * One MySQL CDC source runtime for INITIAL and stream-only startup modes.
 *
 * <p>Mode-specific assignment state remains inside the corresponding SplitAssigner;
 * the reader, enumerator, record emission and serializers are shared.
 */
public final class MySqlSource implements Source<TableRecord, MySqlSplit, PendingSplitsState> {
    private final StartupOptions startupOptions;
    private final MySqlSourceConfig config;
    private final int snapshotChunkSize;
    private final TableMetrics metrics;
    private final MySqlSplitSerializer splitSerializer = MySqlSplitSerializer.INSTANCE;
    private final PendingSplitsStateSerializer stateSerializer = new PendingSplitsStateSerializer(splitSerializer);

    private MySqlSource(StartupOptions options, MySqlSourceConfig config, int chunkSize, TableMetrics metrics) {
        this.startupOptions = Objects.requireNonNull(options, "startupOptions");
        this.config = Objects.requireNonNull(config, "config");
        this.snapshotChunkSize = chunkSize;
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public static MySqlSourceConfigFactory builder() {
        return new MySqlSourceConfigFactory();
    }

    public static MySqlSource create(
            MySqlSourceConfigFactory builder,
            StartupOptions startupOptions,
            int snapshotChunkSize,
            TableMetrics metrics) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(startupOptions, "startupOptions").requireSupported();
        builder.startupOptions(startupOptions);
        return startupOptions.isStreamOnly() ? builder.build(metrics) : builder.buildHybrid(snapshotChunkSize, metrics);
    }

    public static MySqlSource latestOffset(MySqlSourceConfig config, TableMetrics metrics, StartupOptions options) {
        if (!options.isStreamOnly()) {
            throw new IllegalArgumentException("MySQL Binlog Source requires stream-only startup");
        }
        requirePhysicalPrimaryKeys(config);
        config.schemas().keySet().forEach(metrics::register);
        return new MySqlSource(options, config, 0, metrics);
    }

    public static MySqlSource initial(
            MySqlSourceConfig config, int chunkSize, TableMetrics metrics, StartupOptions options) {
        if (options.isStreamOnly()) {
            throw new IllegalArgumentException("MySQL Snapshot Source cannot use stream-only startup");
        }
        config.snapshotServerId(15);
        if (chunkSize < 1 || chunkSize > 100_000) {
            throw new IllegalArgumentException("Snapshot chunk size must be between 1 and 100000");
        }
        config.schemas()
                .forEach((table, schema) -> ChunkUtils.requireSupportedSnapshot(
                        schema, config.chunkKeyColumns().get(table)));
        config.schemas().keySet().forEach(metrics::register);
        return new MySqlSource(options, config, chunkSize, metrics);
    }

    public StartupOptions startupOptions() {
        return startupOptions;
    }

    public StartupMode startupMode() {
        return startupOptions.startupMode();
    }

    public MySqlSourceConfig config() {
        return config;
    }

    public int snapshotChunkSize() {
        if (startupOptions.isStreamOnly()) {
            throw new IllegalStateException("Stream-only MySQL CDC does not use snapshot chunks");
        }
        return snapshotChunkSize;
    }

    @Override
    public void validateParallelism(int parallelism) {
        Source.super.validateParallelism(parallelism);
        if (startupOptions.isStreamOnly() && parallelism != 1) {
            throw new UnsupportedOperationException("Stream-only MySQL CDC requires Source parallelism 1");
        }
        if (!startupOptions.isStreamOnly()) {
            config.snapshotServerId(parallelism - 1);
        }
    }

    @Override
    public Boundedness getBoundedness() {
        return startupOptions.isSnapshotOnly() ? Boundedness.BOUNDED : Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public SplitEnumerator<MySqlSplit, PendingSplitsState> createEnumerator(
            SplitEnumeratorContext<MySqlSplit> context) {
        return createEnumerator(context, null);
    }

    @Override
    public SplitEnumerator<MySqlSplit, PendingSplitsState> restoreEnumerator(
            SplitEnumeratorContext<MySqlSplit> context, PendingSplitsState checkpointState) {
        return createEnumerator(context, Objects.requireNonNull(checkpointState, "checkpointState"));
    }

    private MySqlSourceEnumerator createEnumerator(
            SplitEnumeratorContext<MySqlSplit> context, PendingSplitsState checkpointState) {
        return new MySqlSourceEnumerator(startupOptions, context, config, snapshotChunkSize, checkpointState, metrics);
    }

    @Override
    public SourceReader<TableRecord, MySqlSplit> createReader(SourceReaderContext context) {
        boolean initial = !startupOptions.isStreamOnly();
        if (!initial && (context.currentParallelism() != 1 || context.getIndexOfSubtask() != 0)) {
            throw new IllegalArgumentException("Stream-only MySQL CDC requires one SourceReader");
        }
        if (startupOptions.startupMode() == StartupMode.INITIAL
                && context.getConfiguration()
                        .get(CheckpointingOptions.CHECKPOINTING_INTERVAL)
                        .isZero()) {
            throw new IllegalArgumentException("MySQL Snapshot requires periodic durable checkpoints");
        }
        return new MySqlSourceReader(config.withRuntimeState(context), context, metrics, initial);
    }

    @Override
    public SimpleVersionedSerializer<MySqlSplit> getSplitSerializer() {
        return splitSerializer;
    }

    @Override
    public SimpleVersionedSerializer<PendingSplitsState> getEnumeratorCheckpointSerializer() {
        return stateSerializer;
    }

    private static void requirePhysicalPrimaryKeys(MySqlSourceConfig config) {
        if (config.schemas().values().stream()
                .anyMatch(schema -> schema.primaryKeys().isEmpty())) {
            throw new UnsupportedOperationException(
                    "Keyless MySQL CDC is not executable until Binlog handoff and RETRACT are supported");
        }
    }
}
