package io.yak.ops.boot.config;

import io.yak.ops.business.datasync.DataSyncService;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncExecutionRecovery;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 组装 Data Sync 应用启动后的产品级恢复动作，不承载 Runtime 或 Connector 恢复实现。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Configuration(proxyBeanMethods = false)
public class DataSyncRecoveryConfiguration {

    @Bean
    ApplicationRunner dataSyncRecovery(DataSyncExecutionRecovery executionRecovery, DataSyncService dataSyncService) {
        return arguments -> {
            executionRecovery.recoverExecutions();
            dataSyncService.restoreRealtimeDesiredState();
        };
    }
}
