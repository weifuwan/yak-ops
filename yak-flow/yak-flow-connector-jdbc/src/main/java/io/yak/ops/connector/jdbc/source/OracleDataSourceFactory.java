package io.yak.ops.connector.jdbc.source;

import com.google.auto.service.AutoService;
import io.yak.ops.core.factories.DataSourceFactory;

/** Bounded Oracle JDBC source connector. */
@AutoService(DataSourceFactory.class)
public final class OracleDataSourceFactory extends JdbcDataSourceFactory {
    public OracleDataSourceFactory() {
        super("jdbc-oracle", "ORACLE", "jdbc:oracle:");
    }
}
