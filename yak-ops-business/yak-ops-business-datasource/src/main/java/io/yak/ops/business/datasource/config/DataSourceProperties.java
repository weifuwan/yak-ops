package io.yak.ops.business.datasource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 集中管理 Datasource capability 的连接测试和 Catalog 元数据参数。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@ConfigurationProperties(prefix = "yak.datasource")
public class DataSourceProperties {

    /** 是否启用 Datasource capability。 */
    private boolean enabled = true;
    /** 数据源管理自身的元数据库配置。 */
    private final Database database = new Database();
    /** 连接测试参数。 */
    private final ConnectionTest connectionTest = new ConnectionTest();
    /** Catalog 元数据参数。 */
    private final Catalog catalog = new Catalog();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Database getDatabase() {
        return database;
    }

    public ConnectionTest getConnectionTest() {
        return connectionTest;
    }

    public Catalog getCatalog() {
        return catalog;
    }

    /**
     * 数据源管理自身使用的元数据库配置。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    public static class Database {

        /** 元数据库 JDBC 地址。 */
        private String url = "jdbc:mariadb://127.0.0.1:3306/yak_security"
                + "?useUnicode=true&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"
                + "&useSSL=false&serverTimezone=Asia/Shanghai";
        /** 元数据库用户名。 */
        private String username = "root";
        /** 元数据库密码。 */
        private String password = "123456";
        /** 元数据库 JDBC 驱动类。 */
        private String driverClassName = "org.mariadb.jdbc.Driver";
        /** 连接池最小空闲连接数。 */
        private int minimumIdle = 1;
        /** 连接池最大连接数。 */
        private int maximumPoolSize = 8;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }
    }

    /**
     * 用户配置数据源的连接测试参数。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    public static class ConnectionTest {

        /** 单次连接测试超时时间，单位秒。 */
        private int timeoutSeconds = 5;

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /**
     * Catalog 元数据读取和缓存参数。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    public static class Catalog {

        /** 建立用户数据源连接的超时时间。 */
        private int connectionTimeoutSeconds = 5;

        /** 数据库 / Schema / 表 / 字段元数据缓存 TTL；小于等于 0 时关闭缓存。 */
        private int metadataCacheTtlSeconds = 60;

        /** 下拉远程搜索一次最多返回的表数量。 */
        private int tableSearchLimit = 100;

        /** 单次物理 Catalog 访问超过该阈值时记录慢操作；单位毫秒。 */
        private long slowOperationThresholdMillis = 1000L;

        public int getConnectionTimeoutSeconds() {
            return connectionTimeoutSeconds;
        }

        public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
            this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        }

        public int getMetadataCacheTtlSeconds() {
            return metadataCacheTtlSeconds;
        }

        public void setMetadataCacheTtlSeconds(int metadataCacheTtlSeconds) {
            this.metadataCacheTtlSeconds = metadataCacheTtlSeconds;
        }

        public int getTableSearchLimit() {
            return tableSearchLimit;
        }

        public void setTableSearchLimit(int tableSearchLimit) {
            this.tableSearchLimit = tableSearchLimit;
        }

        public long getSlowOperationThresholdMillis() {
            return slowOperationThresholdMillis;
        }

        public void setSlowOperationThresholdMillis(long slowOperationThresholdMillis) {
            this.slowOperationThresholdMillis = slowOperationThresholdMillis;
        }
    }
}
