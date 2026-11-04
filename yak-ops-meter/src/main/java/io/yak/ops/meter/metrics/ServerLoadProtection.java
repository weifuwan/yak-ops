package io.yak.ops.meter.metrics;

public interface ServerLoadProtection {

    boolean isOverload(SystemMetrics systemMetrics);

}
