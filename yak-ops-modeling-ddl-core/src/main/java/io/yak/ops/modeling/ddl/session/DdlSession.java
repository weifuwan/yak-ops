package io.yak.ops.modeling.ddl.session;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import java.sql.Connection;
import java.sql.SQLException;

/** Owns the JDBC connection and generic datasource catalog for one DDL operation. */
public class DdlSession implements AutoCloseable {

    private final String databaseType;
    private final Connection connection;
    private final DataSourceCatalog catalog;
    private final String database;

    public DdlSession(
            String databaseType, Connection connection, DataSourceCatalog catalog, String database) {
        this.databaseType = databaseType;
        this.connection = connection;
        this.catalog = catalog;
        this.database = database;
    }

    public String getDatabaseType() { return databaseType; }
    public Connection getConnection() { return connection; }
    public DataSourceCatalog getCatalog() { return catalog; }
    public String getDatabase() { return database; }

    @Override
    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
}
