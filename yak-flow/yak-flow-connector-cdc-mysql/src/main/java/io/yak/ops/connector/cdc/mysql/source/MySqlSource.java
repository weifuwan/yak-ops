package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlCdcSourceConfig;
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
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * One MySQL CDC source runtime for INITIAL and stream-only startup modes.
 *
 * <p>Mode-specific assignment state remains inside the corresponding SplitAssigner;
 * the reader, enumerator, record emission and serializers are shared.
 */
public final class MySqlSource implements Source<TableRecord, MySqlSplit, PendingSplitsState> {
    private final StartupOptions startupOptions;
    private final MySqlCdcSourceConfig config;
    private final int snapshotChunkSize;
    private final String fingerprint;
    private final TableMetrics metrics;
    private final MySqlSplitSerializer splitSerializer = new MySqlSplitSerializer();
    private final PendingSplitsStateSerializer stateSerializer = new PendingSplitsStateSerializer();

    private MySqlSource(
            StartupOptions options,
            MySqlCdcSourceConfig config,
            int chunkSize,
            String fingerprint,
            TableMetrics metrics) {
        this.startupOptions = Objects.requireNonNull(options, "startupOptions");
        this.config = Objects.requireNonNull(config, "config");
        this.snapshotChunkSize = chunkSize;
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public static MySqlCdcSourceBuilder builder() {
        return new MySqlCdcSourceBuilder();
    }

    public static MySqlSource create(
            MySqlCdcSourceBuilder builder, StartupOptions startupOptions, int snapshotChunkSize, TableMetrics metrics) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(startupOptions, "startupOptions").requireSupported();
        builder.startupOptions(startupOptions);
        return switch (startupOptions.startupMode()) {
            case INITIAL -> builder.buildHybrid(snapshotChunkSize, metrics);
            case LATEST_OFFSET -> builder.build(metrics);
            default ->
                throw new UnsupportedOperationException(
                        "MySQL CDC startup mode is not implemented: " + startupOptions.asModeString());
        };
    }

    static MySqlSource latestOffset(MySqlCdcSourceConfig config, TableMetrics metrics, StartupOptions options) {
        if (options.startupMode() != StartupMode.LATEST_OFFSET) {
            throw new UnsupportedOperationException("MySQL Binlog Source requires latest-offset startup");
        }
        config.schemas().keySet().forEach(metrics::register);
        return new MySqlSource(options, config, 0, fingerprint(config), metrics);
    }

    static MySqlSource initial(
            MySqlCdcSourceConfig config, int chunkSize, TableMetrics metrics, StartupOptions options) {
        if (options.startupMode() != StartupMode.INITIAL) {
            throw new UnsupportedOperationException("MySQL Snapshot Source requires initial startup");
        }
        config.snapshotServerId(15);
        if (chunkSize < 1 || chunkSize > 100_000) {
            throw new IllegalArgumentException("Snapshot chunk size must be between 1 and 100000");
        }
        config.schemas()
                .forEach((table, schema) -> ChunkUtils.requireSupportedSnapshot(
                        schema, config.chunkKeyColumns().get(table)));
        config.schemas().keySet().forEach(metrics::register);
        return new MySqlSource(
                options, config, chunkSize, fingerprint(config) + "-hybrid-typed-backfill-v3-" + chunkSize, metrics);
    }

    public StartupOptions startupOptions() {
        return startupOptions;
    }

    public StartupMode startupMode() {
        return startupOptions.startupMode();
    }

    public MySqlCdcSourceConfig config() {
        return config;
    }

    public int snapshotChunkSize() {
        if (startupOptions.startupMode() != StartupMode.INITIAL) {
            throw new IllegalStateException("Stream-only MySQL CDC does not use snapshot chunks");
        }
        return snapshotChunkSize;
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public SplitEnumerator<MySqlSplit, PendingSplitsState> createEnumerator(
            SplitEnumeratorContext<MySqlSplit> context) {
        if (startupOptions.startupMode() != StartupMode.INITIAL) requirePhysicalPrimaryKeys(config);
        return createEnumerator(context, null);
    }

    @Override
    public SplitEnumerator<MySqlSplit, PendingSplitsState> restoreEnumerator(
            SplitEnumeratorContext<MySqlSplit> context, PendingSplitsState checkpointState) {
        if (startupOptions.startupMode() != StartupMode.INITIAL) requirePhysicalPrimaryKeys(config);
        return createEnumerator(context, Objects.requireNonNull(checkpointState, "checkpointState"));
    }

    private MySqlSourceEnumerator createEnumerator(
            SplitEnumeratorContext<MySqlSplit> context, PendingSplitsState checkpointState) {
        return new MySqlSourceEnumerator(
                startupOptions, context, config, fingerprint, snapshotChunkSize, checkpointState, metrics);
    }

    @Override
    public SourceReader<TableRecord, MySqlSplit> createReader(SourceReaderContext context) {
        boolean initial = startupOptions.startupMode() == StartupMode.INITIAL;
        if (!initial) requirePhysicalPrimaryKeys(config);
        if (!initial && (context.currentParallelism() != 1 || context.getIndexOfSubtask() != 0)) {
            throw new IllegalArgumentException("Stream-only MySQL CDC requires one SourceReader");
        }
        if (initial
                && context.getConfiguration()
                        .get(CheckpointingOptions.CHECKPOINTING_INTERVAL)
                        .isZero()) {
            throw new IllegalArgumentException("MySQL Snapshot requires periodic durable checkpoints");
        }
        return new MySqlSourceReader(config.withRuntimeState(context), fingerprint, context, metrics, initial);
    }

    @Override
    public SimpleVersionedSerializer<MySqlSplit> getSplitSerializer() {
        return splitSerializer;
    }

    @Override
    public SimpleVersionedSerializer<PendingSplitsState> getEnumeratorCheckpointSerializer() {
        return stateSerializer;
    }

    private static void requirePhysicalPrimaryKeys(MySqlCdcSourceConfig config) {
        if (config.schemas().values().stream()
                .anyMatch(schema -> schema.primaryKeys().isEmpty())) {
            throw new UnsupportedOperationException(
                    "Keyless MySQL CDC is not executable until Binlog handoff and RETRACT are supported");
        }
    }

    private static String fingerprint(MySqlCdcSourceConfig config) {
        StringBuilder definition = new StringBuilder(config.hostname())
                .append('|')
                .append(config.port())
                .append('|')
                .append(config.topicPrefix())
                .append('|')
                .append(config.serverId());
        for (var entry : config.schemas().entrySet()) {
            TableId table = entry.getKey();
            TableSchema schema = entry.getValue();
            definition.append('|').append(table.catalog()).append('/').append(table.table());
            for (Column column : schema.columns()) {
                definition
                        .append('|')
                        .append(column.name())
                        .append(':')
                        .append(column.dataType().asSerializableString());
            }
            definition.append("|pk=").append(String.join(",", schema.primaryKeys()));
            String configured = config.chunkKeyColumns().get(table);
            if (configured != null) definition.append("|chunk=").append(configured);
        }
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(definition.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
