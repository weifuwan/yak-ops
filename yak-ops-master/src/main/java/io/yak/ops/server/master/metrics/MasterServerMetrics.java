package io.yak.ops.server.master.metrics;

import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MasterServerMetrics {

    /**
     * Used to measure the master server is overload.
     */
    private final Counter masterOverloadCounter =
            Counter.builder("ds.master.overload.count")
                    .description("Master server overload count")
                    .register(Metrics.globalRegistry);

    /**
     * Used to measure the number of workflow command consumed by master.
     */
    private final Counter masterConsumeCommandCounter =
            Counter.builder("ds.master.consume.command.count")
                    .description("Master server consume command count")
                    .register(Metrics.globalRegistry);

    private final Counter masterHeartBeatCounter =
            Counter.builder("ds.master.heartbeat.count")
                    .description("master heartbeat count")
                    .register(Metrics.globalRegistry);

    public void registerMasterMemoryAvailableGauge(Supplier<Number> supplier) {
        Gauge.builder("ds.master.memory.available", supplier)
                .description("Master memory available")
                .register(Metrics.globalRegistry);
    }

    public void registerMasterCpuUsageGauge(Supplier<Number> supplier) {
        Gauge.builder("ds.master.cpu.usage", supplier)
                .description("master cpu usage")
                .register(Metrics.globalRegistry);
    }

    public void registerMasterMemoryUsageGauge(Supplier<Number> supplier) {
        Gauge.builder("ds.master.memory.usage", supplier)
                .description("Master memory usage")
                .register(Metrics.globalRegistry);
    }

    public static void registerUncachedException(final Supplier<Number> supplier) {
        Gauge.builder("ds.master.uncached.exception", supplier)
                .description("number of uncached exception")
                .register(Metrics.globalRegistry);
    }

    public void incMasterOverload() {
        masterOverloadCounter.increment();
    }

    public void incMasterConsumeCommand(int commandCount) {
        masterConsumeCommandCounter.increment(commandCount);
    }

    public void incMasterHeartbeatCount() {
        masterHeartBeatCounter.increment();
    }
}
