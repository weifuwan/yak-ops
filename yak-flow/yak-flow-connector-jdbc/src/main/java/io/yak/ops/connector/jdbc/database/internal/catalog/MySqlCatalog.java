package io.yak.ops.connector.jdbc.database.internal.catalog;

import io.yak.ops.connector.jdbc.database.catalog.AbstractJdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.data.TableId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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

    /** Reads actual MySQL uniqueness, not an inferred snapshot chunk key. */
    @Override
    public List<List<String>> getUniqueKeys(TableId tableId) throws SQLException {
        try (Connection connection = connections.getConnection()) {
            String catalog = effectiveCatalog(connection, tableId.catalog());
            Map<String, TreeMap<Integer, String>> indexes = new LinkedHashMap<>();
            Set<String> unsupported = new HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME, SUB_PART FROM information_schema.STATISTICS "
                            + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND NON_UNIQUE = 0 "
                            + "ORDER BY INDEX_NAME, SEQ_IN_INDEX")) {
                statement.setString(1, catalog);
                statement.setString(2, tableId.table());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        String name = rows.getString(1);
                        int sequence = rows.getInt(2);
                        String column = rows.getString(3);
                        Object prefix = rows.getObject(4);
                        if (name == null || sequence < 1 || column == null || prefix != null) {
                            if (name != null) unsupported.add(name);
                            continue;
                        }
                        String previous = indexes.computeIfAbsent(name, ignored -> new TreeMap<>())
                                .putIfAbsent(sequence, column);
                        if (previous != null) unsupported.add(name);
                    }
                }
            }
            List<List<String>> keys = new ArrayList<>();
            for (var index : indexes.entrySet()) {
                if (!unsupported.contains(index.getKey()) && !index.getValue().isEmpty()) {
                    keys.add(List.copyOf(index.getValue().values()));
                }
            }
            return List.copyOf(keys);
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
