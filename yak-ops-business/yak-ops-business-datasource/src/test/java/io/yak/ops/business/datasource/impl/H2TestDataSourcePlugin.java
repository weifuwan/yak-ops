package io.yak.ops.business.datasource.impl;

import com.google.auto.service.AutoService;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Supplies the local H2 database through the same SPI used by production datasource plugins. */
@AutoService(DataSourcePlugin.class)
public final class H2TestDataSourcePlugin implements DataSourcePlugin {

    public static final String JDBC_URL = "jdbc:h2:mem:datasource_unified_catalog;DB_CLOSE_DELAY=-1";

    public static final AtomicInteger CONNECTION_OPENS = new AtomicInteger();

    private static final DataSourceConnection CONNECTION = new DataSourceConnection() {
        @Override
        public String type() {
            return "TEST_H2";
        }

        @Override
        public String jdbcUrl() {
            return JDBC_URL;
        }

        @Override
        public String driverClassName() {
            return "org.h2.Driver";
        }

        @Override
        public String username() {
            return "sa";
        }

        @Override
        public String password() {
            return "";
        }

        @Override
        public String database() {
            return null;
        }

        @Override
        public String schema() {
            return "APP";
        }

        @Override
        public Map<String, String> properties() {
            return Map.of();
        }

        @Override
        public String normalizedJson() {
            return "{}";
        }
    };

    @Override
    public String type() {
        return "TEST_H2";
    }

    @Override
    public DataSourcePluginDescriptor descriptor() {
        return new DataSourcePluginDescriptor(
                "TEST_H2", Set.of(), DataSourcePluginDescriptor.CURRENT_API_VERSION, Set.of(), Set.of());
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder().build();
    }

    @Override
    public DataSourceConnection parseConnection(ReadonlyConfig configuration) {
        return CONNECTION;
    }

    @Override
    public ReadonlyConfig connectionOptions(DataSourceConnection connection, int timeoutSeconds) {
        return new io.yak.ops.configuration.Configuration()
                .set(io.yak.ops.connector.jdbc.JdbcConnectionOptions.URL, JDBC_URL)
                .set(io.yak.ops.connector.jdbc.JdbcConnectionOptions.DRIVER_CLASS, "org.h2.Driver")
                .set(io.yak.ops.connector.jdbc.JdbcConnectionOptions.USERNAME, "sa")
                .set(io.yak.ops.connector.jdbc.JdbcConnectionOptions.PASSWORD, "")
                .set(io.yak.ops.connector.jdbc.JdbcConnectionOptions.TIMEOUT, timeoutSeconds).toReadonlyConfig();
    }

    @Override
    public void testConnection(DataSourceConnection connection, int timeoutSeconds) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Connection openConnection(DataSourceConnection connection, int timeoutSeconds) throws SQLException {
        if (connection != CONNECTION) {
            throw new IllegalArgumentException("Unexpected H2 test connection");
        }
        CONNECTION_OPENS.incrementAndGet();
        return DriverManager.getConnection(JDBC_URL, "sa", "");
    }
}
