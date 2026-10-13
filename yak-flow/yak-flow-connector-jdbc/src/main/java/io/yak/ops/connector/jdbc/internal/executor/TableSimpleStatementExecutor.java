package io.yak.ops.connector.jdbc.internal.executor;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialectConverter;
import io.yak.ops.core.data.RowData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Binds canonical RowData to a single PreparedStatement.
 *
 * <p>No Java-side record buffer is retained; the buffered executor owns pending records
 * and this class holds only the driver's statement batch during a flush.
 */
public final class TableSimpleStatementExecutor implements JdbcBatchStatementExecutor<RowData> {

    private static final Logger LOG = LoggerFactory.getLogger(TableSimpleStatementExecutor.class);

    private final String sql;
    private final JdbcDialectConverter converter;
    private volatile PreparedStatement statement;
    private int pendingRows;

    public TableSimpleStatementExecutor(String sql, JdbcDialectConverter converter) {
        this.sql = Objects.requireNonNull(sql, "sql");
        this.converter = Objects.requireNonNull(converter, "converter");
    }

    @Override
    public void prepareStatements(Connection connection) throws SQLException {
        if (statement != null) {
            throw new IllegalStateException("JDBC statement is already prepared");
        }
        statement = Objects.requireNonNull(connection, "connection").prepareStatement(sql);
    }

    @Override
    public void addToBatch(RowData record) throws SQLException {
        PreparedStatement prepared = requireStatement();
        converter.toExternal(record, prepared);
        prepared.addBatch();
        pendingRows++;
    }

    @Override
    public void executeBatch() throws SQLException {
        long startedNanos = System.nanoTime();
        int rows = pendingRows;
        requireStatement().executeBatch();
        pendingRows = 0;
        if ("true".equals(MDC.get("taskLogDebug"))) {
            LOG.debug(
                    "JDBC statement batch executed, sql={}, statementRows={}, executeMs={}",
                    sql,
                    rows,
                    (System.nanoTime() - startedNanos) / 1_000_000);
        }
    }

    @Override
    public void closeStatements() throws SQLException {
        PreparedStatement prepared = statement;
        statement = null;
        pendingRows = 0;
        if (prepared != null) {
            prepared.close();
        }
    }

    @Override
    public void cancel() throws SQLException {
        PreparedStatement prepared = statement;
        if (prepared != null) {
            prepared.cancel();
        }
    }

    private PreparedStatement requireStatement() {
        PreparedStatement prepared = statement;
        if (prepared == null) {
            throw new IllegalStateException("JDBC statement is not prepared");
        }
        return prepared;
    }
}
