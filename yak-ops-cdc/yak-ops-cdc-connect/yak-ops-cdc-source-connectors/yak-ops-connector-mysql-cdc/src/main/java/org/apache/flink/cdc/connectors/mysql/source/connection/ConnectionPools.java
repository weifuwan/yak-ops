package org.apache.flink.cdc.connectors.mysql.source.connection;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.connectors.mysql.source.config.MySqlSourceConfig;

import com.zaxxer.hikari.HikariDataSource;

/** A JDBC connection pools that consists of {@link HikariDataSource}. */
@Internal
public interface ConnectionPools {

    /**
     * Gets a connection pool from pools, create a new pool if the pool does not exists in the
     * connection pools .
     */
    HikariDataSource getOrCreateConnectionPool(
            ConnectionPoolId poolId, MySqlSourceConfig sourceConfig);
}
