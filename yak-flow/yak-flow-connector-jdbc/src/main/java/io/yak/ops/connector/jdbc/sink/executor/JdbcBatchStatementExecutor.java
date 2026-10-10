package io.yak.ops.connector.jdbc.sink.executor;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Task-local JDBC statement lifecycle.
 *
 * <p>Executors never own connection commits or retries. A batch failure is terminal to
 * the current Writer; the runtime may replay it only from a completed checkpoint.
 *
 * @param <T> input record type
 */
public interface JdbcBatchStatementExecutor<T> {

    void prepareStatements(Connection connection) throws SQLException;

    void addToBatch(T record) throws SQLException;

    void executeBatch() throws SQLException;

    void closeStatements() throws SQLException;

    /** Best-effort cancellation; callers must invoke this off the task mailbox. */
    void cancel() throws SQLException;
}
