package io.yak.ops.meter.metrics;

import lombok.Data;

@Data
public abstract class BaseServerLoadProtectionConfig {

    protected boolean enabled = true;

    protected double maxSystemCpuUsagePercentageThresholds = 0.7;

    protected double maxJvmCpuUsagePercentageThresholds = 0.7;

    protected double maxSystemMemoryUsagePercentageThresholds = 0.7;

    protected double maxDiskUsagePercentageThresholds = 0.7;

}
