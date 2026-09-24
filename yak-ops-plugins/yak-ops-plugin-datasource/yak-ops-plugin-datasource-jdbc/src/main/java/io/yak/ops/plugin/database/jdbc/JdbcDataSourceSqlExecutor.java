package io.yak.ops.plugin.database.jdbc;

import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.DataSourcePluginException.Operation;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** JDBC implementation of one cancellable SQL execution attempt. */
public final class JdbcDataSourceSqlExecutor implements DataSourceSqlExecutor {

    private static final int MAX_TEXT_CHARS = 65_536;
    private static final int MAX_INLINE_BINARY_BYTES = 4_096;

    private final JdbcConnectionProperties connection;
    private final int connectionTimeoutSeconds;
    private final JdbcConnectionProvider connectionProvider;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<Connection> activeConnection = new AtomicReference<>();
    private final AtomicReference<Connection> transactionConnection = new AtomicReference<>();
    private final AtomicReference<Statement> activeStatement = new AtomicReference<>();

    public JdbcDataSourceSqlExecutor(JdbcConnectionProperties connection, int connectionTimeoutSeconds) {
        this(connection, connectionTimeoutSeconds, JdbcDataSourceSqlExecutor::openDirectConnection);
    }

    public JdbcDataSourceSqlExecutor(
            JdbcConnectionProperties connection,
            int connectionTimeoutSeconds,
            JdbcConnectionProvider connectionProvider) {
        this.connection = connection;
        this.connectionTimeoutSeconds = Math.max(1, connectionTimeoutSeconds);
        this.connectionProvider = connectionProvider;
    }

    @Override
    public DataSourceSqlResult execute(DataSourceSqlRequest request) {
        if (cancelled.get()) {
            throw executionError("SQL 执行已取消", null);
        }

        Connection transactional = transactionConnection.get();
        if (transactional != null) {
            try {
                return executeOnConnection(transactional, request);
            } catch (DataSourcePluginException exception) {
                throw exception;
            } catch (Exception exception) {
                throw executionError(safeMessage(exception), exception);
            }
        }

        try {
            try (Connection opened = connectionProvider.open(connection, connectionTimeoutSeconds)) {
                return executeOnConnection(opened, request);
            }
        } catch (DataSourcePluginException exception) {
            throw exception;
        } catch (ClassNotFoundException exception) {
            throw executionError("数据库驱动未安装：" + connection.driverClassName(), exception);
        } catch (Exception exception) {
            throw executionError(safeMessage(exception), exception);
        }
    }

    @Override
    public boolean supportsTransactions() {
        return true;
    }

    @Override
    public synchronized void beginTransaction() {
        if (cancelled.get()) {
            throw executionError("SQL 执行已取消", null);
        }
        if (transactionConnection.get() != null) {
            throw executionError("SQL 事务已经开启", null);
        }

        Connection opened = null;
        try {
            opened = connectionProvider.open(connection, connectionTimeoutSeconds);
            if (cancelled.get()) {
                closeQuietly(opened);
                throw executionError("SQL 执行已取消", null);
            }
            opened.setAutoCommit(false);
            transactionConnection.set(opened);
        } catch (DataSourcePluginException exception) {
            closeQuietly(opened);
            throw exception;
        } catch (ClassNotFoundException exception) {
            closeQuietly(opened);
            throw executionError("数据库驱动未安装：" + connection.driverClassName(), exception);
        } catch (Exception exception) {
            closeQuietly(opened);
            throw executionError("开启 SQL 事务失败：" + safeMessage(exception), exception);
        }
    }

    @Override
    public synchronized void commitTransaction() {
        Connection transactional = requireTransaction();
        try {
            transactional.commit();
        } catch (Exception exception) {
            try {
                transactional.rollback();
            } catch (Exception ignored) {
                // Best effort: closing the connection below is the final cleanup path.
            }
            throw executionError("提交 SQL 事务失败：" + safeMessage(exception), exception);
        } finally {
            transactionConnection.compareAndSet(transactional, null);
            closeQuietly(transactional);
        }
    }

    @Override
    public synchronized void rollbackTransaction() {
        Connection transactional = transactionConnection.getAndSet(null);
        if (transactional == null) return;
        try {
            if (!transactional.isClosed()) transactional.rollback();
        } catch (Exception exception) {
            throw executionError("回滚 SQL 事务失败：" + safeMessage(exception), exception);
        } finally {
            closeQuietly(transactional);
        }
    }

    private Connection requireTransaction() {
        Connection transactional = transactionConnection.get();
        if (transactional == null) {
            throw executionError("SQL 事务尚未开启", null);
        }
        return transactional;
    }

    private DataSourceSqlResult executeOnConnection(Connection opened, DataSourceSqlRequest request) throws Exception {
        activeConnection.set(opened);
        try {
            if (cancelled.get()) {
                throw executionError("SQL 执行已取消", null);
            }
            return request.parameters().isEmpty() ? executePlain(opened, request) : executePrepared(opened, request);
        } finally {
            activeConnection.compareAndSet(opened, null);
        }
    }

    private DataSourceSqlResult executePlain(Connection connection, DataSourceSqlRequest request) throws Exception {
        try (Statement statement = connection.createStatement()) {
            activeStatement.set(statement);
            configureStatement(statement, request);
            boolean hasResultSet = statement.execute(request.sql());
            return readStatementResult(statement, hasResultSet, request.maxRows());
        } finally {
            activeStatement.set(null);
        }
    }

    private DataSourceSqlResult executePrepared(Connection connection, DataSourceSqlRequest request) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(request.sql())) {
            activeStatement.set(statement);
            configureStatement(statement, request);
            for (int index = 0; index < request.parameters().size(); index++) {
                statement.setObject(index + 1, request.parameters().get(index));
            }
            boolean hasResultSet = statement.execute();
            return readStatementResult(statement, hasResultSet, request.maxRows());
        } finally {
            activeStatement.set(null);
        }
    }

    private void configureStatement(Statement statement, DataSourceSqlRequest request) throws Exception {
        try {
            statement.setQueryTimeout(request.timeoutSeconds());
        } catch (SQLFeatureNotSupportedException ignored) {
            // Some JDBC drivers do not implement query timeout. Cancellation still remains available.
        }
        statement.setMaxRows(Math.min(Integer.MAX_VALUE - 1, request.maxRows() + 1));
    }

    private DataSourceSqlResult readStatementResult(Statement statement, boolean hasResultSet, int maxRows)
            throws Exception {
        if (!hasResultSet) {
            return DataSourceSqlResult.updateCount(statement.getUpdateCount());
        }
        try (ResultSet resultSet = statement.getResultSet()) {
            return readResultSet(resultSet, maxRows);
        }
    }

    @Override
    public void cancel() {
        cancelled.set(true);
        Statement statement = activeStatement.get();
        if (statement != null) {
            try {
                statement.cancel();
            } catch (Exception ignored) {
                // Best effort; transaction rollback or connection close remains the cleanup path.
            }
        }

        // In transaction mode keep the connection open so the runtime can explicitly roll it back.
        Connection transactional = transactionConnection.get();
        Connection opened = activeConnection.get();
        if (opened != null && opened != transactional) {
            closeQuietly(opened);
        }
    }

    @Override
    public synchronized void close() {
        Connection transactional = transactionConnection.getAndSet(null);
        if (transactional != null) {
            try {
                if (!transactional.isClosed()) transactional.rollback();
            } catch (Exception ignored) {
                // Best effort cleanup on close.
            } finally {
                closeQuietly(transactional);
            }
        }

        Statement statement = activeStatement.getAndSet(null);
        if (statement != null) {
            try {
                statement.close();
            } catch (Exception ignored) {
                // Best effort cleanup.
            }
        }
        Connection opened = activeConnection.getAndSet(null);
        if (opened != null && opened != transactional) closeQuietly(opened);
    }

    private DataSourceSqlResult readResultSet(ResultSet resultSet, int maxRows) throws Exception {
        ResultSetMetaData metadata = resultSet.getMetaData();
        List<DataSourceSqlColumn> columns = columns(metadata);
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;

        while (resultSet.next()) {
            if (rows.size() >= maxRows) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(metadata.getColumnCount());
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                row.add(normalizeValue(resultSet.getObject(index)));
            }
            rows.add(row);
        }
        return DataSourceSqlResult.resultSet(columns, rows, truncated);
    }

    private List<DataSourceSqlColumn> columns(ResultSetMetaData metadata) throws Exception {
        List<DataSourceSqlColumn> columns = new ArrayList<>(metadata.getColumnCount());
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            String name = metadata.getColumnName(index);
            String label = metadata.getColumnLabel(index);
            columns.add(new DataSourceSqlColumn(
                    name,
                    label == null || label.isBlank() ? name : label,
                    metadata.getColumnTypeName(index),
                    metadata.getColumnType(index),
                    metadata.isNullable(index) != ResultSetMetaData.columnNoNulls));
        }
        return columns;
    }

    private Object normalizeValue(Object value) throws Exception {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return value;
        }
        if (value instanceof String text) {
            return limitText(text);
        }
        if (value instanceof byte[] bytes) {
            if (bytes.length > MAX_INLINE_BINARY_BYTES) {
                return "<BINARY " + bytes.length + " bytes>";
            }
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Blob blob) {
            return "<BLOB " + blob.length() + " bytes>";
        }
        if (value instanceof Clob clob) {
            long length = clob.length();
            int readLength = (int) Math.min(length, MAX_TEXT_CHARS);
            String text = clob.getSubString(1L, readLength);
            return length > MAX_TEXT_CHARS ? text + "…" : text;
        }
        if (value instanceof SQLXML xml) {
            return limitText(xml.getString());
        }
        if (value instanceof java.sql.Date
                || value instanceof java.sql.Time
                || value instanceof java.sql.Timestamp
                || value instanceof java.time.temporal.TemporalAccessor
                || value instanceof java.util.UUID) {
            return value.toString();
        }
        return limitText(String.valueOf(value));
    }

    private String limitText(String value) {
        if (value == null || value.length() <= MAX_TEXT_CHARS) return value;
        return value.substring(0, MAX_TEXT_CHARS) + "…";
    }

    private static Connection openDirectConnection(JdbcConnectionProperties connection, int timeoutSeconds)
            throws Exception {
        Class.forName(connection.driverClassName());
        DriverManager.setLoginTimeout(Math.max(1, timeoutSeconds));
        return DriverManager.getConnection(connection.jdbcUrl(), connectionProperties(connection));
    }

    private static Properties connectionProperties(JdbcConnectionProperties connection) {
        Properties properties = new Properties();
        properties.putAll(connection.properties());
        if (connection.username() != null && !connection.username().isBlank()) {
            properties.setProperty("user", connection.username());
        }
        if (connection.password() != null) {
            properties.setProperty("password", connection.password());
        }
        return properties;
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (Exception ignored) {
            // Best effort cleanup.
        }
    }

    private DataSourcePluginException executionError(String message, Throwable cause) {
        String safe = message == null || message.isBlank() ? "SQL 执行失败" : message;
        return cause == null
                ? new DataSourcePluginException(Operation.EXECUTION, safe)
                : new DataSourcePluginException(Operation.EXECUTION, safe, cause);
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable == null ? "SQL 执行失败" : throwable.getClass().getSimpleName();
        }
        String sanitized = message.replaceAll("(?i)(password|pwd)=([^;&\\s]+)", "$1=******");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }
}
