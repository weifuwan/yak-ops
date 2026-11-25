package io.yak.ops.modeling.ddl.doris;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import java.sql.Connection;
import java.sql.SQLException;

/** Open Doris connection, configured database and plugin catalog sharing one lifecycle. */
public class DorisDataSourceContext implements AutoCloseable {

    private final Connection connection;
    private final DataSourceCatalog catalog;
    private final String database;

    public DorisDataSourceContext(Connection connection, DataSourceCatalog catalog, String database) {
        this.connection = connection;
        this.catalog = catalog;
        this.database = database;
    }

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
