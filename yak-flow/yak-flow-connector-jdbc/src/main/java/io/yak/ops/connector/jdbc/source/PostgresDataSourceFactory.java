package io.yak.ops.connector.jdbc.source;

import com.google.auto.service.AutoService;
import io.yak.ops.core.factories.DataSourceFactory;

/** Bounded Postgres JDBC source connector. */
@AutoService(DataSourceFactory.class)
public final class PostgresDataSourceFactory extends JdbcDataSourceFactory {
    public PostgresDataSourceFactory() {
        super("jdbc-postgres", "POSTGRE_SQL", "jdbc:postgresql:");
    }
}
