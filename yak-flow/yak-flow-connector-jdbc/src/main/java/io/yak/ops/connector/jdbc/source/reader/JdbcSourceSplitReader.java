package io.yak.ops.connector.jdbc.source.reader;

import io.yak.ops.connector.base.source.reader.RecordsBySplits;
import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableRecord;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Blocking JDBC I/O for bounded table splits, owned by one fetcher and one database connection.
 *
 * <p>Each fetch transfers a bounded batch of detached values, which remain valid after a
 * ResultSet is closed. Numeric primary-key cursors resume with an exclusive lower seek bound.
 * Without a supported key the entire split is replayed after recovery (at-least-once only).
 */
public final class JdbcSourceSplitReader implements SplitReader<JdbcRecordAndPosition, JdbcSourceSplit> {

    private final JdbcConnectionOptions connectionOptions;
    private final JdbcDialect dialect;
    private final int fetchBatchSize;
    private final int resultSetFetchSize;
    private final int queryTimeoutSeconds;
    private final Deque<JdbcSourceSplit> pending = new ArrayDeque<>();

    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;
    private JdbcSourceSplit active;
    private boolean hasRow;
    private boolean closed;

    public JdbcSourceSplitReader(
            JdbcConnectionOptions connectionOptions, JdbcDialect dialect, Configuration configuration) {
        this.connectionOptions = Objects.requireNonNull(connectionOptions, "connectionOptions");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(configuration, "configuration");
        fetchBatchSize = configuration.get(JdbcSourceOptions.READER_FETCH_BATCH_SIZE);
        resultSetFetchSize = configuration.get(JdbcSourceOptions.RESULT_SET_FETCH_SIZE);
        queryTimeoutSeconds = configuration.get(JdbcSourceOptions.QUERY_TIMEOUT_SECONDS);
        if (fetchBatchSize <= 0 || resultSetFetchSize <= 0 || queryTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("JDBC read settings must be positive");
        }
    }

    @Override
    public void addSplits(List<JdbcSourceSplit> splits) {
        if (closed) {
            throw new IllegalStateException("JDBC split reader is closed");
        }
        pending.addAll(List.copyOf(splits));
    }

    @Override
    public RecordsWithSplitIds<JdbcRecordAndPosition> fetch() throws Exception {
        if (closed) {
            throw new IllegalStateException("JDBC split reader is closed");
        }
        if (active == null) {
            active = pending.pollFirst();
            if (active == null) {
                return new RecordsBySplits<>(Map.of(), Set.of());
            }
            openSplit(active);
        }

        String splitId = active.splitId();
        List<JdbcRecordAndPosition> batch = new ArrayList<>(Math.min(fetchBatchSize, 1_024));
        for (int remaining = fetchBatchSize; remaining > 0 && hasRow; remaining--) {
            List<Object> values = new ArrayList<>(active.columns().size());
            for (int index = 1; index <= active.columns().size(); index++) {
                values.add(detachValue(resultSet.getObject(index)));
            }
            Long key = active.splitColumn() == null ? null : resultSet.getLong(active.splitColumn());
            TableRecord record = new TableRecord(active.tableId(), RowKind.INSERT, new RowData(values));
            batch.add(new JdbcRecordAndPosition(record, key));
            hasRow = resultSet.next();
        }

        boolean complete = !hasRow;
        if (complete) {
            closeStatementAndResultSet();
            active = null;
        }
        Map<String, List<JdbcRecordAndPosition>> records =
                batch.isEmpty() ? Map.of() : Map.of(splitId, batch);
        return new RecordsBySplits<>(records, complete ? Set.of(splitId) : Set.of());
    }

    @Override
    public void wakeUp() {
        // AddSplits uses the same wakeup protocol as cancellation. Cancelling an active statement
        // here would fail a healthy running split when the next split is assigned; JDBC queries
        // instead have a bounded query timeout and run outside the mailbox.
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;
        SQLException failure = null;
        try {
            closeStatementAndResultSet();
        } catch (SQLException exception) {
            failure = exception;
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            } finally {
                connection = null;
            }
        }
        pending.clear();
        active = null;
        if (failure != null) {
            throw failure;
        }
    }

    private void openSplit(JdbcSourceSplit split) throws SQLException {
        if (connection == null) {
            connection = connectionOptions.openConnection();
            dialect.configureReadConnection(connection);
        }
        closeStatementAndResultSet();
        String columns = split.columns().stream().map(dialect::quoteIdentifier).collect(Collectors.joining(", "));
        String sql = "SELECT " + columns + " FROM " + dialect.qualifiedTable(split.tableId());
        List<Long> bounds = new ArrayList<>(3);
        String key = split.splitColumn();
        if (key != null) {
            String quoted = dialect.quoteIdentifier(key);
            List<String> predicates = new ArrayList<>(3);
            if (split.lowerBound() != null) {
                predicates.add(quoted + " >= ?");
                bounds.add(split.lowerBound());
                predicates.add(quoted + " <= ?");
                bounds.add(split.upperBound());
            }
            if (split.lastEmittedKey() != null) {
                predicates.add(quoted + " > ?");
                bounds.add(split.lastEmittedKey());
            }
            if (!predicates.isEmpty()) {
                sql += " WHERE " + String.join(" AND ", predicates);
            }
            sql += " ORDER BY " + quoted;
        }
        statement = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        statement.setFetchSize(resultSetFetchSize);
        statement.setQueryTimeout(queryTimeoutSeconds);
        for (int index = 0; index < bounds.size(); index++) {
            statement.setLong(index + 1, bounds.get(index));
        }
        resultSet = statement.executeQuery();
        hasRow = resultSet.next();
    }

    private void closeStatementAndResultSet() throws SQLException {
        SQLException failure = null;
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException exception) {
                failure = exception;
            } finally {
                resultSet = null;
            }
        }
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            } finally {
                statement = null;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private Object detachValue(Object value) throws SQLException {
        if (value instanceof Blob blob) {
            long length = blob.length();
            if (length > Integer.MAX_VALUE) {
                throw new SQLException("JDBC binary value exceeds the supported in-memory row size");
            }
            return blob.getBytes(1L, (int) length);
        }
        if (value instanceof Clob clob) {
            long length = clob.length();
            if (length > Integer.MAX_VALUE) {
                throw new SQLException("JDBC character value exceeds the supported in-memory row size");
            }
            return clob.getSubString(1L, (int) length);
        }
        if (value instanceof SQLXML xml) {
            return xml.getString();
        }
        if (value instanceof Array array) {
            try {
                return array.getArray();
            } finally {
                array.free();
            }
        }
        if (value instanceof byte[] bytes) {
            return bytes.clone();
        }
        return value;
    }
}
