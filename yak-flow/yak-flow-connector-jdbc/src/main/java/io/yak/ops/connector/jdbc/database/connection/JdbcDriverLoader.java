package io.yak.ops.connector.jdbc.database.connection;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads versioned JDBC drivers with separate class loaders to isolate vendor driver JARs.
 *
 * <p>Each runtimeId loads once per application lifetime; driver class loaders inherit only the JDK platform loader, not the Yak Ops application loader.
 */
public final class JdbcDriverLoader {

    private static final String DRIVER_DIR_PROPERTY = "yak.ops.jdbc-driver-dir";
    private static final String HOME_PROPERTY = "yak.ops.home";
    private static final JdbcDriverLoader INSTANCE = new JdbcDriverLoader(resolveDriverRoot());

    private final Path driverRoot;
    private final Map<String, Driver> drivers = new ConcurrentHashMap<>();

    private JdbcDriverLoader(Path driverRoot) {
        this.driverRoot = driverRoot.toAbsolutePath().normalize();
    }

    public static JdbcDriverLoader getInstance() {
        return INSTANCE;
    }

    public Driver driver(String runtimeId, String relativeDirectory, String driverClassName) {
        if (runtimeId == null || runtimeId.isBlank()) {
            throw new IllegalArgumentException("JDBC driver identifier must not be blank");
        }
        return drivers.computeIfAbsent(runtimeId, ignored -> loadDriver(relativeDirectory, driverClassName));
    }

    public Connection connect(
            String runtimeId, String relativeDirectory, String driverClassName, String jdbcUrl, Properties properties)
            throws SQLException {
        Driver driver = driver(runtimeId, relativeDirectory, driverClassName);
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(driver.getClass().getClassLoader());
            Connection connection = driver.connect(jdbcUrl, properties);
            if (connection == null) {
                throw new SQLException("JDBC driver does not accept the connection URL");
            }
            return connection;
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    public DriverPropertyInfo[] propertyInfo(
            String runtimeId, String relativeDirectory, String driverClassName, String jdbcUrl, Properties properties)
            throws SQLException {
        Driver driver = driver(runtimeId, relativeDirectory, driverClassName);
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(driver.getClass().getClassLoader());
            return driver.getPropertyInfo(jdbcUrl, properties);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private Driver loadDriver(String relativeDirectory, String driverClassName) {
        Path directory = driverRoot.resolve(relativeDirectory).normalize();
        if (!directory.startsWith(driverRoot)) {
            throw new IllegalArgumentException("JDBC driver directory must be inside the driver root");
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("JDBC driver directory does not exist: " + directory);
        }

        try {
            URL[] urls;
            try (var files = Files.list(directory)) {
                urls = files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jar"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .map(this::toUrl)
                        .toArray(URL[]::new);
            }
            if (urls.length == 0) {
                throw new IllegalStateException("JDBC driver directory contains no JAR files: " + directory);
            }

            URLClassLoader classLoader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
            try {
                Class<?> type = Class.forName(driverClassName, true, classLoader);
                Object instance = type.getDeclaredConstructor().newInstance();
                if (instance instanceof Driver driver) {
                    return driver;
                }
                classLoader.close();
                throw new IllegalStateException(
                        "JDBC driver class does not implement java.sql.Driver: " + driverClassName);
            } catch (Exception exception) {
                classLoader.close();
                throw exception;
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to load JDBC driver: " + driverClassName + ", directory: " + directory, exception);
        }
    }

    private URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JDBC driver JAR location: " + path, exception);
        }
    }

    private static Path resolveDriverRoot() {
        String configured = System.getProperty(DRIVER_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }

        String home = System.getProperty(HOME_PROPERTY);
        if (home != null && !home.isBlank()) {
            return Path.of(home, "jdbc-drivers-builtin");
        }

        Path sourceDirectory = Path.of("yak-ops-dist", "src", "main", "jdbc-drivers-builtin");
        if (Files.isDirectory(sourceDirectory)) {
            return sourceDirectory;
        }

        Path buildDirectory = Path.of("yak-ops-dist", "target", "jdbc-drivers-builtin");
        if (Files.isDirectory(buildDirectory)) {
            return buildDirectory;
        }
        return Path.of("jdbc-drivers-builtin");
    }
}
