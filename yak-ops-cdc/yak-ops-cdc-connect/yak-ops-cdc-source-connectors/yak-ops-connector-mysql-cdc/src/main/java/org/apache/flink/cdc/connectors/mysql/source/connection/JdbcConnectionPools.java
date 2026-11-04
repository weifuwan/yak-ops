package org.apache.flink.cdc.connectors.mysql.source.connection;

import org.apache.flink.cdc.connectors.mysql.source.config.MySqlSourceConfig;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** A Jdbc Connection pools implementation. */
public class JdbcConnectionPools implements ConnectionPools {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcConnectionPools.class);

    private static final JdbcConnectionPools INSTANCE = new JdbcConnectionPools();
    private final Map<ConnectionPoolId, HikariDataSource> pools = new HashMap<>();

    private JdbcConnectionPools() {}

    public static JdbcConnectionPools getInstance() {
        return INSTANCE;
    }

    @Override
    public HikariDataSource getOrCreateConnectionPool(
            ConnectionPoolId poolId, MySqlSourceConfig sourceConfig) {
        synchronized (pools) {
            if (!pools.containsKey(poolId)) {
                LOG.info("Create and register connection pool {}", poolId);
                pools.put(poolId, PooledDataSourceFactory.createPooledDataSource(sourceConfig));
            }
            return pools.get(poolId);
        }
    }

    public void clear() throws IOException {
        synchronized (pools) {
            pools.values().stream().forEach(HikariDataSource::close);
            pools.clear();
        }
    }
}
