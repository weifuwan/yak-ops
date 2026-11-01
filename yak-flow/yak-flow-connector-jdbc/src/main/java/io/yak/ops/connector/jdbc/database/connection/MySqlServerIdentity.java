package io.yak.ops.connector.jdbc.database.connection;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Compares actual MySQL servers without relying on host names, ports or datasource aliases. */
public final class MySqlServerIdentity {
    private MySqlServerIdentity() {}

    /**
     * Opens two authorized, caller-scoped connections and compares server UUIDs.
     *
     * @param first resolved connection options, excluded from persisted task parameters
     * @param second resolved connection options, excluded from persisted task parameters
     * @return whether both connections identify the same MySQL server
     * @throws IllegalStateException if either identity cannot be verified
     */
    public static boolean sameServer(ReadonlyConfig first, ReadonlyConfig second) {
        return identity(first).equals(identity(second));
    }

    private static UUID identity(ReadonlyConfig options) {
        DefaultJdbcConnectionProvider connections =
                new DefaultJdbcConnectionProvider(new JdbcConnectionOptions(options));
        try (Connection connection = connections.getConnection()) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement("SELECT @@server_uuid")) {
                statement.setQueryTimeout(10);
                statement.setMaxRows(1);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new IllegalStateException("MySQL server identity is unavailable");
                    String value = result.getString(1);
                    if (value == null || !value.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
                        throw new IllegalStateException("MySQL server identity is invalid");
                    }
                    return UUID.fromString(value);
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot verify MySQL server identity", failure);
        }
    }
}
