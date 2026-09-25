package io.yak.ops.business.datasource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datasource capability 的最小运行参数。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@ConfigurationProperties(prefix = "yak.datasource")
public class DataSourceProperties {

    /** 是否启用 Datasource capability。 */
    private boolean enabled = true;

    /** 连接测试参数。 */
    private final ConnectionTest connectionTest = new ConnectionTest();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ConnectionTest getConnectionTest() {
        return connectionTest;
    }

    /**
     * 用户配置数据源的连接测试参数。
     *
     * @author weifuwan
     * @since 2026-09-25
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
}
