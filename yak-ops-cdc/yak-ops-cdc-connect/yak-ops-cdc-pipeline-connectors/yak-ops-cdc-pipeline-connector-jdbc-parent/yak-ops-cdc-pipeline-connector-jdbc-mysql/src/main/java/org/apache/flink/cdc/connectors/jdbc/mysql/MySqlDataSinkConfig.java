package org.apache.flink.cdc.connectors.jdbc.mysql;

import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;

import java.io.Serializable;

/** Configurations for MySQL Data Sink. */
public class MySqlDataSinkConfig extends JdbcSinkConfig implements Serializable {
    private MySqlDataSinkConfig(Builder builder) {
        super(builder);
    }

    /** Builder for {@link MySqlDataSinkConfig}. */
    public static class Builder extends JdbcSinkConfig.Builder<Builder> {
        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public MySqlDataSinkConfig build() {
            return new MySqlDataSinkConfig(this);
        }
    }
}
