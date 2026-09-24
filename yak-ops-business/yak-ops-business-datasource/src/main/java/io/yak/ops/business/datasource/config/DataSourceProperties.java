package io.yak.ops.business.datasource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 数据源管理模块配置。 */
@ConfigurationProperties(prefix = "yak.datasource")
public class DataSourceProperties {

    private boolean enabled = true;
    private final Database database = new Database();
    private final ConnectionTest connectionTest = new ConnectionTest();
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

    /** 数据源管理元数据数据库配置。 */
    public static class Database {

        private String url = "jdbc:mariadb://127.0.0.1:3306/yak_security"
                + "?useUnicode=true&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"
                + "&useSSL=false&serverTimezone=Asia/Shanghai";
        private String username = "root";
        private String password = "123456";
        private String driverClassName = "org.mariadb.jdbc.Driver";
        private int minimumIdle = 1;
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

    /** 用户配置的数据源连接测试参数。 */
    public static class ConnectionTest {

        private int timeoutSeconds = 5;

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /** Catalog 元数据和轻量读取参数。 */
    public static class Catalog {

        /** 建立用户数据源连接的超时时间。 */
        private int connectionTimeoutSeconds = 5;

        /** SQL describe / preview / count 的 statement 级超时时间。 */
        private int queryTimeoutSeconds = 15;

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

        public int getQueryTimeoutSeconds() {
            return queryTimeoutSeconds;
        }

        public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
            this.queryTimeoutSeconds = queryTimeoutSeconds;
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
