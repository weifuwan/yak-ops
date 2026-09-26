package io.yak.ops.plugin.database.jdbc.runtime;

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
 * 通过独立 ClassLoader 加载版本化 JDBC Driver，避免同一 Vendor 的多个 Driver Jar 污染应用 ClassLoader。
 *
 * <p>每个 runtimeId 在应用生命周期内只加载一次；Driver ClassLoader 只继承 JDK Platform ClassLoader，不继承 Yak Ops
 * 应用 ClassLoader。</p>
 *
 * @author weifuwan
 * @since 2026-09-26
 */
public final class IsolatedJdbcDriverRuntime {

    private static final String DRIVER_DIR_PROPERTY = "yak.ops.jdbc-driver-dir";
    private static final String HOME_PROPERTY = "yak.ops.home";
    private static final IsolatedJdbcDriverRuntime INSTANCE = new IsolatedJdbcDriverRuntime(resolveDriverRoot());

    private final Path driverRoot;
    private final Map<String, Driver> drivers = new ConcurrentHashMap<>();

    private IsolatedJdbcDriverRuntime(Path driverRoot) {
        this.driverRoot = driverRoot.toAbsolutePath().normalize();
    }

    public static IsolatedJdbcDriverRuntime getInstance() {
        return INSTANCE;
    }

    public Driver driver(String runtimeId, String relativeDirectory, String driverClassName) {
        if (runtimeId == null || runtimeId.isBlank()) {
            throw new IllegalArgumentException("JDBC Driver runtimeId 不能为空");
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
                throw new SQLException("JDBC Driver 不支持当前连接地址");
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
            throw new IllegalArgumentException("JDBC Driver 目录必须位于驱动根目录下");
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("JDBC Driver 目录不存在：" + directory);
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
                throw new IllegalStateException("JDBC Driver 目录中没有 Jar：" + directory);
            }

            URLClassLoader classLoader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
            try {
                Class<?> type = Class.forName(driverClassName, true, classLoader);
                Object instance = type.getDeclaredConstructor().newInstance();
                if (instance instanceof Driver driver) {
                    return driver;
                }
                classLoader.close();
                throw new IllegalStateException("JDBC Driver 类未实现 java.sql.Driver：" + driverClassName);
            } catch (Exception exception) {
                classLoader.close();
                throw exception;
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("JDBC Driver 加载失败：" + driverClassName + "，目录：" + directory, exception);
        }
    }

    private URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (Exception exception) {
            throw new IllegalStateException("JDBC Driver Jar 地址无效：" + path, exception);
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

        Path buildDirectory = Path.of("yak-ops-dist", "target", "jdbc-drivers-builtin");
        if (Files.isDirectory(buildDirectory)) {
            return buildDirectory;
        }
        return Path.of("jdbc-drivers-builtin");
    }
}
