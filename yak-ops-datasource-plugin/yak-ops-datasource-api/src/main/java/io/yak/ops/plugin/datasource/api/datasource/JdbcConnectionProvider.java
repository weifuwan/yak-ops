package io.yak.ops.plugin.datasource.api.datasource;

import java.sql.Connection;
import java.sql.SQLException;

public interface JdbcConnectionProvider {

    Connection getConnection() throws SQLException;

}
