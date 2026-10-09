package io.yak.ops.connector.jdbc.source.reader;

import io.yak.ops.connector.base.source.reader.RecordsBySplits;
import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.connection.DriverManagerJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionRetry;
import io.yak.ops.connector.jdbc.database.converter.JdbcDialectConverter;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    private final JdbcConnectionProvider connections;
    private final JdbcDialect dialect;
    private final int fetchBatchSize;
    private final int resultSetFetchSize;
    private final int queryTimeoutSeconds;
    private final int connectionAttempts;
    private final Deque<JdbcSourceSplit> pending = new ArrayDeque<>();

    private Connection connection;
    private PreparedStatement statement;
    private volatile PreparedStatement cancellableStatement;
    private volatile boolean cancellationRequested;
    private ResultSet resultSet;
    private JdbcDialectConverter converter;
    private JdbcSourceSplit active;
    private boolean hasRow;
    private boolean closed;

    public JdbcSourceSplitReader(
            JdbcConnectionOptions connectionOptions, JdbcDialect dialect, Configuration configuration) {
        this(new DriverManagerJdbcConnectionProvider(connectionOptions), dialect, configuration);
    }

    public JdbcSourceSplitReader(JdbcConnectionProvider connections, JdbcDialect dialect, Configuration configuration) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(configuration, "configuration");
        fetchBatchSize = configuration.get(JdbcSourceOptions.READER_FETCH_BATCH_SIZE);
        resultSetFetchSize = configuration.get(JdbcSourceOptions.RESULT_SET_FETCH_SIZE);
        queryTimeoutSeconds = configuration.get(JdbcSourceOptions.QUERY_TIMEOUT_SECONDS);
        connectionAttempts = configuration.get(JdbcSourceOptions.CONNECTION_ATTEMPTS);
        if (fetchBatchSize <= 0 || resultSetFetchSize <= 0 || queryTimeoutSeconds <= 0
                || connectionAttempts <= 0) {
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
        if (closed || cancellationRequested) {
            throw new IllegalStateException("JDBC split reader is closed or cancelled");
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
            if (cancellationRequested) {
                throw new SQLException("JDBC source read was cancelled");
            }
            RowData values = converter.toInternal(resultSet);
            Long key = active.splitColumn() == null ? null : resultSet.getLong(active.splitColumn());
            TableRecord record = new TableRecord(active.tableId(), RowKind.INSERT, values);
            batch.add(new JdbcRecordAndPosition(record, key));
            hasRow = resultSet.next();
        }

        boolean complete = !hasRow;
        if (complete) {
            closeStatementAndResultSet();
            active = null;
        }
        Map<String, List<JdbcRecordAndPosition>> records = batch.isEmpty() ? Map.of() : Map.of(splitId, batch);
        return new RecordsBySplits<>(records, complete ? Set.of(splitId) : Set.of());
    }

    @Override
    public void wakeUp() {
        // Normal split assignments must not interrupt an active JDBC query.
    }

    @Override
    public void cancel() {
        cancellationRequested = true;
        PreparedStatement running = cancellableStatement;
        if (running == null) {
            return;
        }
        // Some JDBC drivers implement Statement.cancel() with blocking network I/O. The mailbox
        // must never wait here; the fetcher close timeout still bounds shutdown of the reader.
        Thread.ofVirtual().name("yak-jdbc-query-cancel").start(() -> {
            try {
                running.cancel();
            } catch (SQLException ignored) {
                // An already closed/failed statement still terminates through the fetcher.
            }
        });
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
            connection = JdbcConnectionRetry.open(connections, connectionAttempts);
            dialect.configureReadConnection(connection);
        }
        if (cancellationRequested) {
            throw new SQLException("JDBC source read was cancelled");
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
        cancellableStatement = statement;
        if (cancellationRequested) {
            throw new SQLException("JDBC source read was cancelled");
        }
        statement.setFetchSize(resultSetFetchSize);
        statement.setQueryTimeout(queryTimeoutSeconds);
        for (int index = 0; index < bounds.size(); index++) {
            statement.setLong(index + 1, bounds.get(index));
        }
        resultSet = statement.executeQuery();
        converter = dialect.createRowConverter(resultSet.getMetaData());
        // A restored split must not silently change its column order or identity.
        if (converter.schema().columnCount() != split.columns().size()) {
            throw new SQLException("JDBC ResultSet shape changed since split planning");
        }
        for (int index = 0; index < split.columns().size(); index++) {
            if (!split.columns()
                    .get(index)
                    .equals(converter.schema().column(index).name())) {
                throw new SQLException("JDBC ResultSet column identity changed since split planning");
            }
        }
        hasRow = resultSet.next();
    }

    private void closeStatementAndResultSet() throws SQLException {
        cancellableStatement = null;
        converter = null;
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
}
