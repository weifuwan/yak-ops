package io.yak.ops.spi.datasource.execution;

/** Fresh datasource SQL executor used by one execution attempt. */
public interface DataSourceSqlExecutor extends AutoCloseable {

    DataSourceSqlResult execute(DataSourceSqlRequest request);

    /** Whether this executor can keep one datasource transaction across multiple execute calls. */
    default boolean supportsTransactions() {
        return false;
    }

    /** Begin a transaction that remains active across subsequent execute calls. */
    default void beginTransaction() {
        throw new UnsupportedOperationException("Datasource SQL executor does not support transactions");
    }

    /** Commit the active transaction. */
    default void commitTransaction() {
        throw new UnsupportedOperationException("Datasource SQL executor does not support transactions");
    }

    /** Roll back the active transaction. */
    default void rollbackTransaction() {
        throw new UnsupportedOperationException("Datasource SQL executor does not support transactions");
    }

    /** Best-effort cancellation for a currently running statement. */
    default void cancel() {
        // Optional capability.
    }

    @Override
    default void close() {
        // Optional cleanup capability.
    }
}
