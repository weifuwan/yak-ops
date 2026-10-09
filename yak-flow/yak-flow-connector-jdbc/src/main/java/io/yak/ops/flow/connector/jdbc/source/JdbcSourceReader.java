package io.yak.ops.flow.connector.jdbc.source;

import io.yak.ops.flow.api.checkpoint.CheckpointState;
import io.yak.ops.flow.api.row.YakRow;
import io.yak.ops.flow.api.source.SourceReader;
import io.yak.ops.flow.api.trace.RuntimeTraceListener;
import io.yak.ops.flow.connector.jdbc.JdbcNumericSplitConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSourceConfig;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialect;
import io.yak.ops.flow.connector.jdbc.trace.JdbcSourceSplitTraceEvent;
import io.yak.ops.flow.connector.jdbc.trace.JdbcTraceEventType;
import io.yak.ops.flow.connector.jdbc.trace.JdbcTraceFailureStage;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 使用 forward-only JDBC cursor 分批读取一个表 split，并按 YakTableSchema 顺序产出 YakRow。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class JdbcSourceReader implements SourceReader<JdbcSourceSplit> {

    private final JdbcSourceConfig config;
    private final JdbcConnectionProvider connectionProvider;
    private final JdbcDialect dialect;
    private final RuntimeTraceListener traceListener;

    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;
    private boolean finished;
    private long rowsRead;
    private JdbcSourceSplit currentSplit;
    private String currentSql;
    private long splitStartNanos;
    private boolean terminalTraceEmitted;

    JdbcSourceReader(
            JdbcSourceConfig config,
            JdbcConnectionProvider connectionProvider,
            JdbcDialect dialect,
            RuntimeTraceListener traceListener) {
        this.config = config;
        this.connectionProvider = connectionProvider;
        this.dialect = dialect;
        this.traceListener = traceListener;
    }

    @Override
    public void open(JdbcSourceSplit split) throws Exception {
        if (!config.table().equals(split.table())) {
            throw new IllegalArgumentException("JDBC Source split 与配置表不匹配");
        }
        if (split.isRangeSplit() && !matchesRangeSplit(split)) {
            throw new IllegalArgumentException("JDBC Source split 与分片配置不匹配");
        }

        currentSplit = split;
        currentSql = split.isRangeSplit()
                ? dialect.selectRangeSql(split.table(), config.schema(), split.splitColumn())
                : dialect.selectSql(split.table(), config.schema());
        splitStartNanos = System.nanoTime();
        rowsRead = 0L;
        finished = false;
        terminalTraceEmitted = false;
        emitTrace(JdbcTraceEventType.SOURCE_SPLIT_STARTED, 0L, null, null);

        try {
            connection = connectionProvider.open(config.connection(), config.timeoutSeconds());
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            if (connection.getMetaData().supportsTransactionIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ)) {
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            }

            statement =
                    connection.prepareStatement(currentSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            if (split.isRangeSplit()) {
                statement.setLong(1, split.lowerBoundInclusive());
                statement.setLong(2, split.upperBoundInclusive());
            }
            statement.setFetchSize(config.fetchSize());
            statement.setQueryTimeout(config.timeoutSeconds());
            resultSet = statement.executeQuery();
        } catch (Exception exception) {
            emitFailure(JdbcTraceFailureStage.SOURCE_OPEN, exception);
            throw exception;
        }
    }

    @Override
    public List<YakRow> poll() throws Exception {
        if (finished) {
            return List.of();
        }

        try {
            List<YakRow> rows = new ArrayList<>(config.readBatchSize());
            while (rows.size() < config.readBatchSize()) {
                if (!resultSet.next()) {
                    finished = true;
                    emitFinished();
                    break;
                }
                List<Object> values = new ArrayList<>(config.schema().columnCount());
                for (int index = 1; index <= config.schema().columnCount(); index++) {
                    values.add(normalizeValue(resultSet.getObject(index)));
                }
                rows.add(new YakRow(io.yak.ops.flow.api.row.RowKind.INSERT, values));
                rowsRead++;
            }
            return rows;
        } catch (Exception exception) {
            emitFailure(JdbcTraceFailureStage.SOURCE_READ, exception);
            throw exception;
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public CheckpointState snapshotState(long checkpointId) {
        return new JdbcReaderState(rowsRead);
    }

    @Override
    public void restore(CheckpointState state) {
        if (!(state instanceof JdbcReaderState jdbcState)) {
            throw new IllegalArgumentException("JDBC Reader checkpoint state 类型不匹配");
        }
        if (jdbcState.rowsRead() != 0) {
            throw new UnsupportedOperationException("当前 JDBC batch checkpoint 不支持跨执行恢复");
        }
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        try {
            if (resultSet != null) resultSet.close();
        } catch (Exception exception) {
            failure = exception;
        }
        try {
            if (statement != null) statement.close();
        } catch (Exception exception) {
            if (failure == null) failure = exception;
        }
        try {
            if (connection != null) connection.close();
        } catch (Exception exception) {
            if (failure == null) failure = exception;
        }
        if (failure != null) throw failure;
    }

    private void emitFinished() {
        if (terminalTraceEmitted) return;
        terminalTraceEmitted = true;
        emitTrace(JdbcTraceEventType.SOURCE_SPLIT_FINISHED, elapsedMillis(), null, null);
    }

    private void emitFailure(JdbcTraceFailureStage stage, Exception exception) {
        if (terminalTraceEmitted) return;
        terminalTraceEmitted = true;
        emitTrace(JdbcTraceEventType.SOURCE_SPLIT_FAILED, elapsedMillis(), stage, exception);
    }

    private void emitTrace(
            JdbcTraceEventType eventType,
            long durationMillis,
            JdbcTraceFailureStage failureStage,
            Exception exception) {
        JdbcSourceSplit split = currentSplit;
        if (split == null) return;
        List<Long> parameters =
                split.isRangeSplit() ? List.of(split.lowerBoundInclusive(), split.upperBoundInclusive()) : List.of();
        traceListener.emit(new JdbcSourceSplitTraceEvent(
                Instant.now(),
                eventType,
                split.splitId(),
                Thread.currentThread().getName(),
                eventType == JdbcTraceEventType.SOURCE_SPLIT_STARTED ? currentSql : null,
                parameters,
                split.splitColumn(),
                split.lowerBoundInclusive(),
                split.upperBoundInclusive(),
                rowsRead,
                durationMillis,
                failureStage,
                exception == null ? null : exception.getClass().getName(),
                exception == null ? null : exception.getMessage()));
    }

    private long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - splitStartNanos));
    }

    private boolean matchesRangeSplit(JdbcSourceSplit split) {
        if (config.splitConfig() != null) {
            return config.splitConfig().column().equals(split.splitColumn());
        }
        if (config.splitSize() == null) return false;
        return JdbcNumericSplitConfig.eligibleColumn(config.schema())
                .filter(split.splitColumn()::equals)
                .isPresent();
    }

    private Object normalizeValue(Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return value;
    }
}
