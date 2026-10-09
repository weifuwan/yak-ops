package io.yak.ops.connector.jdbc.database;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Discovers the single JDBC factory capable of handling one JDBC URL.
 *
 * <p>Ambiguous or missing providers are errors, not a silent fallback to a generic SQL dialect.
 * Connection URLs are deliberately excluded from exception messages because they can contain
 * inline credentials.
 */
public final class JdbcFactoryLoader {

    private JdbcFactoryLoader() {}

    public static JdbcDialect loadDialect(String jdbcUrl) {
        return loadDialect(jdbcUrl, contextClassLoader());
    }

    public static JdbcDialect loadDialect(String jdbcUrl, ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        try {
            List<JdbcFactory> factories = new ArrayList<>();
            ServiceLoader.load(JdbcFactory.class, classLoader).forEach(factories::add);
            return resolve(jdbcUrl, factories).createDialect();
        } catch (ServiceConfigurationError failure) {
            throw new IllegalStateException("Could not discover JDBC factories", failure);
        }
    }

    static JdbcFactory resolve(String jdbcUrl, Iterable<JdbcFactory> providers) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
            throw new IllegalArgumentException("A JDBC URL is required");
        }
        JdbcFactory match = null;
        for (JdbcFactory provider : providers) {
            if (provider.acceptsURL(jdbcUrl)) {
                if (match != null) {
                    throw new IllegalStateException("More than one JDBC factory accepts this database");
                }
                match = provider;
            }
        }
        if (match == null) {
            throw new IllegalStateException("No installed JDBC factory accepts this database");
        }
        return match;
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? JdbcFactoryLoader.class.getClassLoader() : loader;
    }
}
