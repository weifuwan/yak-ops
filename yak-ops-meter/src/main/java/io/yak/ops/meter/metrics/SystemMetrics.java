package io.yak.ops.meter.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMetrics {

    // CPU
    private double systemCpuUsagePercentage;
    private double jvmCpuUsagePercentage;

    // JVM-Memory
    // todo: get pod memory usage
    private double jvmMemoryUsed;
    private double jvmMemoryMax;
    private double jvmHeapUsed;
    private double jvmNonHeapUsed;
    private double jvmHeapMax;
    private double jvmNonHeapMax;
    private double jvmMemoryUsedPercentage;

    // System-Memory
    // todo: get pod cpu usage
    private double systemMemoryUsed;
    private double systemMemoryMax;
    private double systemMemoryUsedPercentage;

    // Disk
    // todo: get pod disk usage
    private double diskUsed;
    private double diskTotal;
    private double diskUsedPercentage;

}
