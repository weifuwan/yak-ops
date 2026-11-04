package io.yak.ops.server.worker.config;

import io.yak.ops.meter.metrics.SystemMetrics;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorkerServerLoadProtectionTest {

    @Test
    void isOverload() {
        WorkerConfig workerConfig = new WorkerConfig();
        WorkerServerLoadProtection workerServerLoadProtection = new WorkerServerLoadProtection(workerConfig);
        SystemMetrics systemMetrics = SystemMetrics.builder()
                .jvmMemoryUsedPercentage(0.71)
                .systemMemoryUsedPercentage(0.71)
                .systemCpuUsagePercentage(0.71)
                .jvmCpuUsagePercentage(0.71)
                .diskUsedPercentage(0.71)
                .build();

        workerConfig.getServerLoadProtection().setEnabled(false);
        Assertions.assertFalse(workerServerLoadProtection.isOverload(systemMetrics));

        workerConfig.getServerLoadProtection().setEnabled(true);
        Assertions.assertTrue(workerServerLoadProtection.isOverload(systemMetrics));
    }
}
