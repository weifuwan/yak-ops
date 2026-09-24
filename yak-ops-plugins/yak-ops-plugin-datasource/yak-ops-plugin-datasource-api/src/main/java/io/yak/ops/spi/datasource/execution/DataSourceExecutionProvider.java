package io.yak.ops.spi.datasource.execution;

/**
 * Runtime bridge that resolves a durable datasource reference into a fresh physical SQL executor.
 *
 * <p>The provider owns datasource lookup and credential resolution. Callers only persist/reference
 * datasource IDs and never receive connection secrets.
 */
public interface DataSourceExecutionProvider {

    DataSourceSqlExecutor open(String dataSourceReference);
}
