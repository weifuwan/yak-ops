package org.apache.flink.cdc.connectors.jdbc.factory;

import org.apache.flink.cdc.common.configuration.ConfigOption;
import org.apache.flink.cdc.common.configuration.ConfigOptions;

/** Configuration options for the bounded JDBC source. */
public final class JdbcSourceOptions {

    private JdbcSourceOptions() {}

    public static final ConfigOption<String> URL =
            ConfigOptions.key("url").stringType().noDefaultValue();
    public static final ConfigOption<String> DRIVER_NAME =
            ConfigOptions.key("driver-name").stringType().noDefaultValue();
    public static final ConfigOption<String> USERNAME =
            ConfigOptions.key("username").stringType().defaultValue("");
    public static final ConfigOption<String> PASSWORD =
            ConfigOptions.key("password").stringType().defaultValue("");
    public static final ConfigOption<String> TABLES =
            ConfigOptions.key("tables").stringType().noDefaultValue();
    public static final ConfigOption<Integer> FETCH_SIZE =
            ConfigOptions.key("scan.fetch-size").intType().defaultValue(1000);
    public static final ConfigOption<Integer> SPLIT_SIZE =
            ConfigOptions.key("scan.split-size").intType().defaultValue(100000);
}
