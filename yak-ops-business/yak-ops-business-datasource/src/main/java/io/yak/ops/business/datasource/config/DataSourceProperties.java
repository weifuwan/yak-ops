package io.yak.ops.business.datasource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Datasource capability 的最小运行参数。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Component
@ConfigurationProperties(prefix = "yak.datasource")
public class DataSourceProperties {

    /** 单次连接测试超时时间，单位秒。 */
    private int connectionTestTimeoutSeconds = 5;

    public int getConnectionTestTimeoutSeconds() {
        return connectionTestTimeoutSeconds;
    }

    public void setConnectionTestTimeoutSeconds(int connectionTestTimeoutSeconds) {
        this.connectionTestTimeoutSeconds = connectionTestTimeoutSeconds;
    }
}
