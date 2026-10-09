package io.yak.ops.boot.config;

import io.yak.ops.business.datasync.history.DataSyncHistoryRecovery;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 启动时只处理旧产品 Execution 遗留状态；不装配已删除的离线/实时同步引擎。
 */
@Configuration(proxyBeanMethods = false)
public class DataSyncRecoveryConfiguration {

    @Bean
    ApplicationRunner dataSyncHistoryRecovery(DataSyncHistoryRecovery historyRecovery) {
        return arguments -> historyRecovery.closeAbandonedExecutions();
    }
}
