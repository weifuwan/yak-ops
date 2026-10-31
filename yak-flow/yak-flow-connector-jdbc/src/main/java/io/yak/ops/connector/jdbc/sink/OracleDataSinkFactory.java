package io.yak.ops.connector.jdbc.sink;

import com.google.auto.service.AutoService;
import io.yak.ops.core.factories.DataSinkFactory;

/** Oracle JDBC event sink connector. */
@AutoService(DataSinkFactory.class)
public final class OracleDataSinkFactory extends JdbcDataSinkFactory {
    public OracleDataSinkFactory() {
        super("jdbc-oracle", "ORACLE", "jdbc:oracle:");
    }
}
