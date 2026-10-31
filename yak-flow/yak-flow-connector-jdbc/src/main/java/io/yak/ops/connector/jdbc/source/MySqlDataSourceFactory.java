package io.yak.ops.connector.jdbc.source;

import com.google.auto.service.AutoService;
import io.yak.ops.core.factories.DataSourceFactory;

/** Bounded MySql JDBC source connector. */
@AutoService(DataSourceFactory.class)
public final class MySqlDataSourceFactory extends JdbcDataSourceFactory {
    public MySqlDataSourceFactory() {
        super("jdbc-mysql", "MYSQL", "jdbc:mysql:");
    }
}
