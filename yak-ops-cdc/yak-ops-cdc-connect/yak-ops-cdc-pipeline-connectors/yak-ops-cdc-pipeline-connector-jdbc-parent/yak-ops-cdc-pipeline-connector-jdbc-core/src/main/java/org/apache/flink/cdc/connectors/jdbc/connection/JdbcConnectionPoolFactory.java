package org.apache.flink.cdc.connectors.jdbc.connection;

import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.Serializable;
import java.sql.DriverManager;

/** A factory class for creating pooled {@link HikariDataSource}. */
public abstract class JdbcConnectionPoolFactory implements Serializable {

    public static final String CONNECTION_POOL_PREFIX = "connection-pool-";
    public static final String SERVER_TIMEZONE_KEY = "serverTimezone";
    public static final int MINIMUM_POOL_SIZE = 1;

    public static HikariDataSource createPooledDataSource(JdbcSinkConfig sinkConfig) {
        final HikariConfig config = new HikariConfig();
        DriverManager.getDrivers();

        String hostName = sinkConfig.getHostname();
        int port = sinkConfig.getPort();

        config.setPoolName(CONNECTION_POOL_PREFIX + hostName + ":" + port);
        config.setJdbcUrl(sinkConfig.getConnUrl());
        config.setUsername(sinkConfig.getUsername());
        config.setPassword(sinkConfig.getPassword());
        config.setMinimumIdle(MINIMUM_POOL_SIZE);
        config.setMaximumPoolSize(sinkConfig.getConnectionPoolSize());
        config.setConnectionTimeout(sinkConfig.getConnectTimeout().toMillis());
        config.setDriverClassName(sinkConfig.getDriverClassName());

        // note: the following properties should be optional (only applied to MySQL)
        config.addDataSourceProperty(SERVER_TIMEZONE_KEY, sinkConfig.getServerTimeZone());
        // optional optimization configurations for pooled DataSource
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        return new HikariDataSource(config);
    }

    /**
     * The reuse strategy of connection pools. In most situations, connections to different
     * databases in same instance (which means same host and port) can reuse same connection pool.
     * However, in some situations when different databases in same instance cannot reuse same
     * connection pool to connect, such as postgresql, this method should be overridden.
     */
    public static ConnectionPoolId getPoolId(
            JdbcSinkConfig sinkConfig, String dataSourcePoolFactoryIdentifier) {
        return new ConnectionPoolId(
                sinkConfig.getHostname(),
                sinkConfig.getPort(),
                sinkConfig.getUsername(),
                null,
                dataSourcePoolFactoryIdentifier);
    }
}
