package io.yak.ops.server.master.config;

import io.yak.ops.meter.metrics.BaseServerLoadProtection;
import io.yak.ops.meter.metrics.SystemMetrics;
import io.yak.ops.server.master.engine.IWorkflowRepository;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MasterServerLoadProtection extends BaseServerLoadProtection {

    private final IWorkflowRepository workflowRepository;

    private final MasterServerLoadProtectionConfig masterServerLoadProtectionConfig;

    public MasterServerLoadProtection(IWorkflowRepository workflowRepository,
                                      MasterConfig masterConfig) {
        super(masterConfig.getServerLoadProtection());
        this.masterServerLoadProtectionConfig = masterConfig.getServerLoadProtection();
        this.workflowRepository = workflowRepository;
    }

    @Override
    public boolean isOverload(SystemMetrics systemMetrics) {
        if (!masterServerLoadProtectionConfig.isEnabled()) {
            return false;
        }

        if (super.isOverload(systemMetrics)) {
            return true;
        }

        // Check workflow instance count
        int currentWorkflowInstanceCount = workflowRepository.getAll().size();
        if (currentWorkflowInstanceCount >= masterServerLoadProtectionConfig.getMaxConcurrentWorkflowInstances()) {
            log.info(
                    "OverLoad: the workflow instance count: {} exceeds the maxConcurrentWorkflowInstances {}",
                    currentWorkflowInstanceCount, masterServerLoadProtectionConfig.getMaxConcurrentWorkflowInstances());
            return true;
        }
        return false;
    }
}
