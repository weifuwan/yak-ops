package io.yak.ops.alert.service;

import static com.google.common.truth.Truth.assertThat;

import io.yak.ops.alert.config.AlertConfig;

import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertSenderThreadPoolFactoryTest {

    private final AlertConfig alertConfig = new AlertConfig();

    private final AlertSenderThreadPoolFactory alertSenderThreadPoolFactory =
            new AlertSenderThreadPoolFactory(alertConfig);

    @Test
    void getThreadPool() {
        ThreadPoolExecutor threadPool = alertSenderThreadPoolFactory.getThreadPool();
        assertThat(threadPool.getCorePoolSize()).isEqualTo(alertConfig.getSenderParallelism());
        assertThat(threadPool.getMaximumPoolSize()).isEqualTo(alertConfig.getSenderParallelism());
    }
}
