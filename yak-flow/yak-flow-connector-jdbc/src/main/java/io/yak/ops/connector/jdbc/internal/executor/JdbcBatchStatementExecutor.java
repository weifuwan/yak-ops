package io.yak.ops.connector.jdbc.internal.executor;

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

    /**
     * Prepares task-owned statements on the provided, already-open connection.
     *
     * @param connection caller-owned JDBC connection; executor never closes or commits it
     * @throws SQLException if statement preparation fails
     */
    void prepareStatements(Connection connection) throws SQLException;

    /**
     * Binds a detached input record to the current statement group.
     *
     * @param record values already normalized and copied by the owning batch output
     * @throws SQLException if statement binding or an ordered group switch fails
     */
    void addToBatch(T record) throws SQLException;

    /**
     * Executes all pending JDBC statement groups on the existing transaction.
     *
     * <p>Executing the batch does not commit the connection or confirm checkpoint durability.
     *
     * @throws SQLException if any statement group fails
     */
    void executeBatch() throws SQLException;

    /**
     * Releases prepared statements without committing or flushing caller-owned records.
     *
     * @throws SQLException if a JDBC statement cannot be closed
     */
    void closeStatements() throws SQLException;

    /**
     * Requests best-effort cancellation of running JDBC I/O without committing.
     *
     * <p>Drivers may block inside Statement.cancel(); callers must not invoke this on the
     * task mailbox or wait for cancellation to finish.
     *
     * @throws SQLException if the driver rejects the cancellation request
     */
    void cancel() throws SQLException;
}
