package io.yak.ops.connector.jdbc.sink;

import com.google.auto.service.AutoService;
import io.yak.ops.core.factories.DataSinkFactory;

/** MySql JDBC event sink connector. */
@AutoService(DataSinkFactory.class)
public final class MySqlDataSinkFactory extends JdbcDataSinkFactory {
    public MySqlDataSinkFactory() {
        super("jdbc-mysql", "MYSQL", "jdbc:mysql:");
    }
}
