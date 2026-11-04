package org.apache.flink.cdc.connectors.mysql.source.connection;

import org.apache.flink.cdc.connectors.mysql.source.config.MySqlSourceConfig;
import org.apache.flink.util.FlinkRuntimeException;

import com.zaxxer.hikari.HikariDataSource;
import io.debezium.jdbc.JdbcConfiguration;
import io.debezium.jdbc.JdbcConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/** A factory to create JDBC connection for MySQL. */
public class JdbcConnectionFactory implements JdbcConnection.ConnectionFactory {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcConnectionFactory.class);

    private final MySqlSourceConfig sourceConfig;

    public JdbcConnectionFactory(MySqlSourceConfig sourceConfig) {
        this.sourceConfig = sourceConfig;
    }

    @Override
    public Connection connect(JdbcConfiguration config) throws SQLException {
        final int connectRetryTimes = sourceConfig.getConnectMaxRetries();

        final ConnectionPoolId connectionPoolId =
                new ConnectionPoolId(
                        sourceConfig.getHostname(),
                        sourceConfig.getPort(),
                        sourceConfig.getUsername());

        HikariDataSource dataSource =
                JdbcConnectionPools.getInstance()
                        .getOrCreateConnectionPool(connectionPoolId, sourceConfig);

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
