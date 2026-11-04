package io.yak.ops.server.worker.config;

import io.yak.ops.meter.metrics.BaseServerLoadProtection;
import io.yak.ops.meter.metrics.SystemMetrics;
import io.yak.ops.server.worker.executor.PhysicalTaskExecutorContainerProvider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkerServerLoadProtection extends BaseServerLoadProtection {

    @Autowired
    private PhysicalTaskExecutorContainerProvider physicalTaskExecutorContainerDelegator;

    public WorkerServerLoadProtection(WorkerConfig workerConfig) {
        super(workerConfig.getServerLoadProtection());
    }

    @Override
    public boolean isOverload(SystemMetrics systemMetrics) {
        if (!baseServerLoadProtectionConfig.isEnabled()) {
            return false;
        }

        if (super.isOverload(systemMetrics)) {
            return true;
        }

        if (physicalTaskExecutorContainerDelegator.getExecutorContainer().slotUsage() == 1) {
            log.info("OverLoad: the TaskExecutorContainer slot usage is 1");
            return true;
        }
        return false;
    }
}
