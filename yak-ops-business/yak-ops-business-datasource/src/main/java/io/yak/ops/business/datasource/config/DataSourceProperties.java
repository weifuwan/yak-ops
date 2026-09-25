package io.yak.ops.business.datasource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Datasource Service Layer 的可调运行参数。
 *
 * <p>只承载真实运行时配置，不作为功能开关，也不重复持有应用自身数据库连接配置。</p>
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
