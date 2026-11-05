package io.yak.ops.api.service;

import io.yak.ops.plugin.datasource.api.datasource.JdbcDriverConnectionProvider;
import io.yak.ops.plugin.datasource.api.utils.DataSourceUtils;
import io.yak.ops.spi.enums.DbType;

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
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Reads installed JDBC driver metadata; never opens a connection or accepts a client JDBC URL.
 */
@Service
public class JdbcDriverPropertyService {

    private static final String MYSQL_METADATA_URL = "jdbc:mysql://127.0.0.1:3306/yak_ops";

    private static final String POSTGRESQL_METADATA_URL = "jdbc:postgresql://127.0.0.1:5432/yak_ops";

    private static final Set<String> EXCLUDED_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "user", "username", "password", "host", "port", "database", "dbname", "databasename",
            "allowloadlocalinfile", "allowlocalinfile", "allowurlinlocalinfile", "autodeserialize")));

    public DriverProperties getProperties(DbType type) {
        if (type != DbType.MYSQL && type != DbType.POSTGRESQL) {
            throw new IllegalArgumentException("Only MYSQL and POSTGRESQL support driver property discovery");
        }
        String driverClassName = DataSourceUtils.getDatasourceDriver(type);
        String metadataUrl = type == DbType.MYSQL ? MYSQL_METADATA_URL : POSTGRESQL_METADATA_URL;
        try {
            Driver driver = JdbcDriverConnectionProvider.instantiateDriver(driverClassName);
            DriverPropertyInfo[] infos = driver.getPropertyInfo(metadataUrl, new java.util.Properties());
            List<Property> properties = toProperties(infos);
            return new DriverProperties(type.name(), driverClassName,
                    driver.getMajorVersion() + "." + driver.getMinorVersion(),
                    properties.stream().map(Property::getName).collect(Collectors.toList()), properties);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot load JDBC property metadata for " + type, e);
        }
    }

    static List<Property> toProperties(DriverPropertyInfo[] infos) {
        if (infos == null || infos.length == 0) {
            return Collections.emptyList();
        }
        List<Property> properties = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DriverPropertyInfo info : infos) {
            if (info == null || !isSafeProperty(info.name)) {
                continue;
            }
            if (!seen.add(info.name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            properties.add(new Property(info.name, info.description == null ? "" : info.description,
                    info.value, info.required,
                    info.choices == null ? Collections.emptyList() : Arrays.asList(info.choices)));
        }
        properties.sort(Comparator.comparing(Property::getName, String.CASE_INSENSITIVE_ORDER));
        return properties;
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

    @Getter
    @AllArgsConstructor
    public static class DriverProperties {

        private final String dbType;

        private final String driverClassName;

        private final String driverVersion;

        private final List<String> acceptedPropertyKeys;

        private final List<Property> properties;
    }

    @Getter
    @AllArgsConstructor
    public static class Property {

        private final String name;

        private final String description;

        /** A suggestion only; never automatically saved to the datasource. */
        private final String defaultValue;

        private final boolean required;

        private final List<String> choices;
    }
}
