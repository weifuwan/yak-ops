package io.yak.ops.plugin.datasource.api.catalog;

import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;
import io.yak.ops.plugin.datasource.api.datasource.JdbcDriverConnectionProvider;

import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Shared JDBC property metadata extraction for datasource-specific catalogs.
 * Only server-defined driver class names and metadata URLs should be supplied.
 */
public final class JdbcConnectionPropertyCatalog {

    private static final Set<String> EXCLUDED_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "user", "username", "password", "host", "port", "database", "dbname", "databasename",
            "allowloadlocalinfile", "allowlocalinfile", "allowurlinlocalinfile", "autodeserialize")));

    private JdbcConnectionPropertyCatalog() {
    }

    public static List<ConnectionProperty> getConnectionProperties(String driverClassName, String metadataUrl) {
        try {
            Driver driver = JdbcDriverConnectionProvider.instantiateDriver(driverClassName);
            // JDBC metadata discovery does not use actual datasource credentials or open a connection.
            return toProperties(driver.getPropertyInfo(metadataUrl, new Properties()));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to discover properties from JDBC driver: " + driverClassName, e);
        }
    }

    public static List<ConnectionProperty> toProperties(DriverPropertyInfo[] infos) {
        if (infos == null || infos.length == 0) {
            return Collections.emptyList();
        }

        List<ConnectionProperty> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (DriverPropertyInfo info : infos) {
            if (info == null || !isSafeProperty(info.name)) {
                continue;
            }
            if (!names.add(info.name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            result.add(new ConnectionProperty(info.name,
                    info.description == null ? "" : info.description,
                    info.value,
                    info.required,
                    info.choices == null ? Collections.emptyList() : Arrays.asList(info.choices)));
        }
        result.sort(Comparator.comparing(ConnectionProperty::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static boolean isSafeProperty(String name) {
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9._-]*")) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        return !EXCLUDED_KEYS.contains(normalized)
                && !normalized.contains("password")
                && !normalized.contains("secret");
    }
}
