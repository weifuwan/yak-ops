package io.yak.ops.meter.metrics;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BaseServerLoadProtection implements ServerLoadProtection {

    protected final BaseServerLoadProtectionConfig baseServerLoadProtectionConfig;

    public BaseServerLoadProtection(BaseServerLoadProtectionConfig baseServerLoadProtectionConfig) {
        this.baseServerLoadProtectionConfig = baseServerLoadProtectionConfig;
    }

    @Override
    public boolean isOverload(SystemMetrics systemMetrics) {
        if (!baseServerLoadProtectionConfig.isEnabled()) {
            return false;
        }
        if (systemMetrics.getSystemCpuUsagePercentage() > baseServerLoadProtectionConfig
                .getMaxSystemCpuUsagePercentageThresholds()) {
//            log.info(
//                    "OverLoad: the system cpu usage: {} is over then the maxSystemCpuUsagePercentageThresholds {}",
//                    systemMetrics.getSystemCpuUsagePercentage(),
//                    baseServerLoadProtectionConfig.getMaxSystemCpuUsagePercentageThresholds());
            return true;
        }
        if (systemMetrics.getJvmCpuUsagePercentage() > baseServerLoadProtectionConfig
                .getMaxJvmCpuUsagePercentageThresholds()) {
//            log.info(
//                    "OverLoad: the jvm cpu usage: {} is over then the maxJvmCpuUsagePercentageThresholds {}",
//                    systemMetrics.getJvmCpuUsagePercentage(),
//                    baseServerLoadProtectionConfig.getMaxJvmCpuUsagePercentageThresholds());
            return true;
        }
        if (systemMetrics.getDiskUsedPercentage() > baseServerLoadProtectionConfig
                .getMaxDiskUsagePercentageThresholds()) {
//            log.info("OverLoad: the DiskUsedPercentage: {} is over then the maxDiskUsagePercentageThresholds {}",
//                    systemMetrics.getDiskUsedPercentage(),
//                    baseServerLoadProtectionConfig.getMaxDiskUsagePercentageThresholds());
            return true;
        }
        if (systemMetrics.getSystemMemoryUsedPercentage() > baseServerLoadProtectionConfig
                .getMaxSystemMemoryUsagePercentageThresholds()) {
//            log.info(
//                    "OverLoad: the SystemMemoryUsedPercentage: {} is over then the maxSystemMemoryUsagePercentageThresholds {}",
//                    systemMetrics.getSystemMemoryUsedPercentage(),
//                    baseServerLoadProtectionConfig.getMaxSystemMemoryUsagePercentageThresholds());
            return true;
        }
        return false;
    }
}
