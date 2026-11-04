package org.apache.flink.cdc.connectors.base.relational.connection;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.connectors.base.config.SourceConfig;

/** A pool collection that consists of multiple connection pools. */
@Experimental
public interface ConnectionPools<P, C extends SourceConfig> {

    /**
     * Gets a connection pool from pools, create a new pool if the pool does not exists in the
     * connection pools .
     */
    P getOrCreateConnectionPool(ConnectionPoolId poolId, C sourceConfig);
}
