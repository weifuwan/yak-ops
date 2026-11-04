package io.yak.ops.spi.datasource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface used to get connection of a data source.
 */
public interface DataSourceClient extends AutoCloseable {

    Connection getConnection() throws SQLException;

}
