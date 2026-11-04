package org.apache.flink.cdc.connectors.jdbc.connection;

import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;
import org.apache.flink.util.FlinkRuntimeException;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;

/** Factory class for creating JDBC {@link Connection}s. */
public class JdbcConnectionFactory implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcConnectionFactory.class);

    private final JdbcSinkConfig sinkConfig;

    public JdbcConnectionFactory(JdbcSinkConfig sinkConfig) {
        this.sinkConfig = sinkConfig;
    }

    public Connection connect() throws SQLException {
        final int connectRetryTimes = sinkConfig.getConnectMaxRetries();

        final ConnectionPoolId connectionPoolId =
                JdbcConnectionPoolFactory.getPoolId(
                        sinkConfig, JdbcConnectionPoolFactory.class.getName());

        HikariDataSource dataSource =
                JdbcConnectionPools.getInstance()
                        .getOrCreateConnectionPool(connectionPoolId, sinkConfig);

        int i = 0;
        while (i < connectRetryTimes) {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                if (i < connectRetryTimes - 1) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ie) {
                        throw new FlinkRuntimeException(
                                "Failed to get connection, interrupted while doing another attempt",
                                ie);
                    }
                    LOG.warn("Get connection failed, retry times {}", i + 1);
                } else {
                    LOG.error("Get connection failed after retry {} times", i + 1);
                    throw new FlinkRuntimeException(e);
                }
            }
            i++;
        }
        return dataSource.getConnection();
    }
}
