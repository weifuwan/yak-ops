package io.yak.ops.connector.jdbc.source.reader;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.source.reader.SingleThreadMultiplexSourceReaderBase;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.TableRecord;
import java.util.Map;
import java.util.Objects;

/**
 * A JDBC SourceReader that multiplexes independently assigned table splits through one fetcher.
 *
 * <p>Blocking JDBC operations run on the fetcher thread; output, split progress and checkpoint
 * snapshots remain confined to the owning Runtime mailbox.
 */
public final class JdbcSourceReader
        extends SingleThreadMultiplexSourceReaderBase<
                JdbcRecordAndPosition, TableRecord, JdbcSourceSplit, JdbcSourceSplitState> {

    private final TableMetrics metrics;

    public JdbcSourceReader(
            JdbcConnectionOptions connection,
            JdbcDialect dialect,
            Configuration configuration,
            SourceReaderContext context) {
        this(new DefaultJdbcConnectionProvider(connection), dialect, configuration, context);
    }

    public JdbcSourceReader(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            Configuration configuration,
            SourceReaderContext context) {
        this(connections, dialect, configuration, context, new TableMetrics());
    }

    /** Creates one mailbox-owned reader with shared table observations. */
    public JdbcSourceReader(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            Configuration configuration,
            SourceReaderContext context,
            TableMetrics metrics) {
        super(
                () -> new JdbcSourceSplitReader(connections, dialect, configuration, metrics),
                new JdbcRecordEmitter(metrics),
                Objects.requireNonNull(context, "context"));
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public void start() {
        if (snapshotState(0).isEmpty()) {
            context.sendSplitRequest();
        }
    }

    @Override
    protected JdbcSourceSplitState initializedState(JdbcSourceSplit split) {
        return new JdbcSourceSplitState(split);
    }

    @Override
    protected JdbcSourceSplit toSplitType(String splitId, JdbcSourceSplitState state) {
        return state.toCheckpointSplit();
    }

    @Override
    protected void onSplitFinished(Map<String, JdbcSourceSplitState> finished) {
        finished.values()
                .forEach(
                        state -> metrics.splitFinished(state.toCheckpointSplit().tableId()));
        context.reportCompletedSplits(finished.size());
        context.sendSplitRequest();
    }
}
