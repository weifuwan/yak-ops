package org.apache.flink.cdc.connectors.jdbc.connection;

import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;

/** Factory class for creating connection pools. */
public interface ConnectionPools<P, C extends JdbcSinkConfig> extends AutoCloseable {

    /**
     * Create a new pool if the pool does not exist, or get existing connection pool with given
     * identifier.
     */
    P getOrCreateConnectionPool(ConnectionPoolId poolId, C sinkConfig);
}
