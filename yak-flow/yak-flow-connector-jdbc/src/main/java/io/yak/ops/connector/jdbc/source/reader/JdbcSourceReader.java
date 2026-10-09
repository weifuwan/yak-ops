package io.yak.ops.connector.jdbc.source.reader;

import io.yak.ops.connector.base.source.reader.SingleThreadMultiplexSourceReaderBase;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.configuration.Configuration;
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

    public JdbcSourceReader(
            JdbcConnectionOptions connection,
            JdbcDialect dialect,
            Configuration configuration,
            SourceReaderContext context) {
        super(
                () -> new JdbcSourceSplitReader(connection, dialect, configuration),
                new JdbcRecordEmitter(),
                Objects.requireNonNull(context, "context"));
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
        context.sendSplitRequest();
    }
}
