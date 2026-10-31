package io.yak.ops.connector.jdbc.sink;

import com.google.auto.service.AutoService;
import io.yak.ops.core.factories.DataSinkFactory;

/** Postgres JDBC event sink connector. */
@AutoService(DataSinkFactory.class)
public final class PostgresDataSinkFactory extends JdbcDataSinkFactory {
    public PostgresDataSinkFactory() {
        super("jdbc-postgres", "POSTGRE_SQL", "jdbc:postgresql:");
    }
}
