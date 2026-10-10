package io.yak.ops.connector.jdbc.sink.executor;

import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The only retained-record buffer inside JDBC's BatchOutput.
 *
 * <p>The driver receives statements only during executeBatch. Records remain available
 * until the OutputFormat confirms a successful commit; no implicit retry is attempted
 * after an ambiguous JDBC failure.
 */
public final class TableBufferedStatementExecutor implements JdbcBatchStatementExecutor<RowData> {

    private final JdbcBatchStatementExecutor<RowData> statementExecutor;
    private final List<RowData> pending = new ArrayList<>();

    public TableBufferedStatementExecutor(JdbcBatchStatementExecutor<RowData> statementExecutor) {
        this.statementExecutor = Objects.requireNonNull(statementExecutor, "statementExecutor");
    }

    @Override
    public void prepareStatements(Connection connection) throws SQLException {
        statementExecutor.prepareStatements(connection);
    }

    @Override
    public void addToBatch(RowData record) {
        Objects.requireNonNull(record, "record");
        GenericRowData detached = new GenericRowData(record.getArity());
        for (int index = 0; index < record.getArity(); index++) {
            detached.setField(index, record.getField(index));
        }
        pending.add(detached);
    }

    public int bufferedRecords() {
        return pending.size();
    }

    @Override
    public void executeBatch() throws SQLException {
        for (RowData row : pending) {
            statementExecutor.addToBatch(row);
        }
        statementExecutor.executeBatch();
    }

    /** Called only after the owning JDBC connection confirms commit. */
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
