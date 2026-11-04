package org.apache.flink.cdc.connectors.jdbc.options;

import org.apache.flink.cdc.common.configuration.ConfigOption;
import org.apache.flink.cdc.common.configuration.ConfigOptions;
import org.apache.flink.cdc.common.configuration.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Configurations for JDBC data source. */
public class JdbcSinkOptions {
    public static final ConfigOption<String> CONN_URL =
            ConfigOptions.key("url")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("JDBC connection URL for sink database.");

    public static final ConfigOption<String> USERNAME =
            ConfigOptions.key("username")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Name of the database to use when connecting to the database server.");

    public static final ConfigOption<String> PASSWORD =
            ConfigOptions.key("password")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Password to use when connecting to the database server.");
    public static final ConfigOption<String> DRIVER_CLASS_NAME =
            ConfigOptions.key("driver-class-name")
                    .stringType()
                    .defaultValue("com.mysql.cj.jdbc.Driver")
                    .withDescription("Table name of the database connection to driver class.");

    public static final ConfigOption<String> SERVER_TIME_ZONE =
            ConfigOptions.key("server-time-zone")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "The session time zone in database server. If not set, then "
                                    + "ZoneId.systemDefault() is used to determine the server time zone.");

    public static final ConfigOption<Duration> CONNECT_TIMEOUT =
            ConfigOptions.key("connect.timeout")
                    .durationType()
                    .defaultValue(Duration.ofSeconds(30))
                    .withDescription(
                            "The maximum time that the connector should wait after trying to connect to the database server before timing out.");

    public static final ConfigOption<Integer> CONNECTION_POOL_SIZE =
            ConfigOptions.key("connection.pool.size")
                    .intType()
                    .defaultValue(20)
                    .withDescription("The connection pool size.");

    public static final ConfigOption<Integer> CONNECT_MAX_RETRIES =
            ConfigOptions.key("connect.max-retries")
                    .intType()
                    .defaultValue(3)
                    .withDescription(
                            "The max retry times that the connector should retry to build database server connection.");

    public static final ConfigOption<Long> WRITE_BATCH_INTERVAL_MS =
            ConfigOptions.key("write.batch.interval.ms")
                    .longType()
                    .defaultValue(0L)
                    .withDescription(
                            "The flush interval mills, over this time, asynchronous threads will flush data. Can be set to '0' to disable it.");

    public static final ConfigOption<Integer> WRITE_MAX_OPEN_TABLES =
            ConfigOptions.key("write.max-open-tables")
                    .intType()
                    .defaultValue(32)
                    .withDescription(
                            "Maximum number of open JDBC DML table writers per Sink subtask. "
                                    + "Least recently used tables are flushed and closed before reopening.");

    public static final ConfigOption<Integer> WRITE_BATCH_SIZE =
            ConfigOptions.key("write.batch.size")
                    .intType()
                    .defaultValue(512)
                    .withDescription(
                            "The maximum amount of records in a single writing batch. Batching is only supported for tables with primary keys.");

    public static final ConfigOption<Integer> WRITE_MAX_RETRIES =
            ConfigOptions.key("write.max.retries")
                    .intType()
                    .defaultValue(3)
                    .withDescription("The max retry times if writing records to database failed.");

    public static final String JDBC_PROPERTIES_PROP_PREFIX = "jdbc.properties.";

    public static Map<String, String> getPropertiesByPrefix(
            Configuration tableOptions, String prefix) {
        final Map<String, String> props = new HashMap<>();

        for (Map.Entry<String, String> entry : tableOptions.toMap().entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String subKey = entry.getKey().substring(prefix.length());
                props.put(subKey, entry.getValue());
            }
        }
        return props;
    }
}
