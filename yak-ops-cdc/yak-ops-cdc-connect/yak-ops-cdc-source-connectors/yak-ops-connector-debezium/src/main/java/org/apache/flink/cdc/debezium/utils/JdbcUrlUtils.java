package org.apache.flink.cdc.debezium.utils;

import java.util.Map;
import java.util.Properties;

/** Option utils for JDBC URL properties. */
public class JdbcUrlUtils {

    // Prefix for JDBC specific properties.
    public static final String PROPERTIES_PREFIX = "jdbc.properties.";

    public static Properties getJdbcProperties(Map<String, String> tableOptions) {
        Properties jdbcProperties = new Properties();
        if (hasJdbcProperties(tableOptions)) {
            tableOptions.keySet().stream()
                    .filter(key -> key.startsWith(PROPERTIES_PREFIX))
                    .forEach(
                            key -> {
                                final String value = tableOptions.get(key);
                                final String subKey = key.substring((PROPERTIES_PREFIX).length());
                                jdbcProperties.put(subKey, value);
                            });
        }
        return jdbcProperties;
    }

    /**
     * Decides if the table options contains JDBC properties that start with prefix
     * 'jdbc.properties'.
     */
    private static boolean hasJdbcProperties(Map<String, String> tableOptions) {
        return tableOptions.keySet().stream().anyMatch(k -> k.startsWith(PROPERTIES_PREFIX));
    }
}
