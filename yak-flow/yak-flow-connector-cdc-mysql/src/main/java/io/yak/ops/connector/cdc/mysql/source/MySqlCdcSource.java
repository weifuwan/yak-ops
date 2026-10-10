package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlPendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlSourceEnumerator;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlSourceReader;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplitSerializer;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
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
 * Reads MySQL Binlog changes through Debezium as an unbounded YakFlow Source.
 *
 * <p>One Source owns a single Binlog split across its selected tables. Debezium produces
 * events off the mailbox, while Connector Base delivers detached rows on the mailbox
 * and checkpoints the last successfully emitted offset and required Schema History.
 *
 * <p>PR1 deliberately excludes snapshot/backfill, arbitrary historical startup offsets,
 * automatic schema evolution and end-to-end exactly-once delivery.
 */
public final class MySqlCdcSource implements Source<TableRecord, MySqlBinlogSplit, MySqlPendingSplitsState> {

    private final MySqlCdcSourceConfig config;
    private final String fingerprint;
    private final MySqlBinlogSplitSerializer splitSerializer = new MySqlBinlogSplitSerializer();
    private final MySqlPendingSplitsStateSerializer stateSerializer = new MySqlPendingSplitsStateSerializer();

    MySqlCdcSource(MySqlCdcSourceConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        fingerprint = fingerprint(config);
    }

    public static MySqlCdcSourceBuilder builder() {
        return new MySqlCdcSourceBuilder();
    }

    public MySqlCdcSourceConfig config() {
        return config;
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public SplitEnumerator<MySqlBinlogSplit, MySqlPendingSplitsState> createEnumerator(
            SplitEnumeratorContext<MySqlBinlogSplit> context) {
        return new MySqlSourceEnumerator(context, fingerprint, null);
    }

    @Override
    public SplitEnumerator<MySqlBinlogSplit, MySqlPendingSplitsState> restoreEnumerator(
            SplitEnumeratorContext<MySqlBinlogSplit> context, MySqlPendingSplitsState checkpointState) {
        return new MySqlSourceEnumerator(context, fingerprint, Objects.requireNonNull(checkpointState, "checkpoint"));
    }

    @Override
    public SourceReader<TableRecord, MySqlBinlogSplit> createReader(SourceReaderContext context) {
        if (context.currentParallelism() != 1 || context.getIndexOfSubtask() != 0) {
            throw new IllegalArgumentException("MySQL Binlog Source PR1 requires exactly one SourceReader");
        }
        return new MySqlSourceReader(config, fingerprint, context);
    }

    @Override
    public SimpleVersionedSerializer<MySqlBinlogSplit> getSplitSerializer() {
        return splitSerializer;
    }

    @Override
    public SimpleVersionedSerializer<MySqlPendingSplitsState> getEnumeratorCheckpointSerializer() {
        return stateSerializer;
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
