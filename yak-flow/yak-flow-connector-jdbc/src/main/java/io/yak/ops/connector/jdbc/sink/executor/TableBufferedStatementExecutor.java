package io.yak.ops.connector.jdbc.sink.executor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Single retained-record buffer owned by JDBC's BatchOutput.
 *
 * <p>Records are detached and validated upon arrival. The delegate owns prepared statements
 * but never a second row buffer. Pending records are cleared only after a confirmed commit;
 * uncertain commits are not retried.
 *
 * @param <T> input record type
 */
public final class TableBufferedStatementExecutor<T> implements JdbcBatchStatementExecutor<T> {

    private final JdbcBatchStatementExecutor<T> statementExecutor;
    private final UnaryOperator<T> snapshot;
    private final List<T> pending = new ArrayList<>();

    public TableBufferedStatementExecutor(
            JdbcBatchStatementExecutor<T> statementExecutor, UnaryOperator<T> snapshot) {
        this.statementExecutor = Objects.requireNonNull(statementExecutor, "statementExecutor");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    @Override
    public void prepareStatements(Connection connection) throws SQLException {
        statementExecutor.prepareStatements(connection);
    }

    @Override
    public void addToBatch(T record) {
        pending.add(Objects.requireNonNull(snapshot.apply(Objects.requireNonNull(record, "record")), "snapshot"));
    }

    public int bufferedRecords() {
        return pending.size();
    }

    @Override
    public void executeBatch() throws SQLException {
        for (T value : pending) {
            statementExecutor.addToBatch(value);
        }
        statementExecutor.executeBatch();
    }

    /** Called only after the owning JDBC transaction has committed successfully. */
    public void acknowledgeCommit() {
        pending.clear();
    }

    @Override
    public void closeStatements() throws SQLException {
        try {
            statementExecutor.closeStatements();
        } finally {
            pending.clear();
        }
    }

    @Override
    public void cancel() throws SQLException {
        statementExecutor.cancel();
    }
}
