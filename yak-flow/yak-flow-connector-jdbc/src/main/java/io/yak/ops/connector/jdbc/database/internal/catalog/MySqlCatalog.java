package io.yak.ops.connector.jdbc.database.internal.catalog;

import io.yak.ops.connector.jdbc.database.catalog.AbstractJdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * MySQL uses JDBC catalogs as database namespaces and has no separate schema namespace.
 */
public final class MySqlCatalog extends AbstractJdbcCatalog {

    public MySqlCatalog(JdbcConnectionProvider connections, JdbcDialect dialect) {
        super(connections, dialect);
    }

    @Override
    public List<String> listDatabases() throws SQLException {
        try (Connection connection = connections.getConnection();
                ResultSet catalogs = connection.getMetaData().getCatalogs()) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            while (catalogs.next()) {
                String name = catalogs.getString("TABLE_CAT");
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
            return List.copyOf(names);
        }
    }

    @Override
    public List<String> listSchemas(String database) {
        return List.of();
    }

    @Override
    protected String effectiveSchema(Connection connection, String requested) {
        return null;
    }
}
