package io.yak.ops.alert.metrics;

import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AlertServerMetrics {

    private final Counter alertSuccessCounter =
            Counter.builder("ds.alert.send.count")
                    .tag("status", "success")
                    .description("Alert success count")
                    .register(Metrics.globalRegistry);

    private final Counter alertFailCounter =
            Counter.builder("ds.alert.send.count")
                    .tag("status", "fail")
                    .description("Alert failure count")
                    .register(Metrics.globalRegistry);

    private final Counter alertHeartBeatCounter =
            Counter.builder("ds.alert.heartbeat.count")
                    .description("alert heartbeat count")
                    .register(Metrics.globalRegistry);

    public void registerPendingAlertGauge(final Supplier<Number> supplier) {
        Gauge.builder("ds.alert.pending", supplier)
                .description("Number of pending alert")
                .register(Metrics.globalRegistry);
    }

    public static void registerUncachedException(final Supplier<Number> supplier) {
        Gauge.builder("ds.alert.uncached.exception", supplier)
                .description("number of uncached exception")
                .register(Metrics.globalRegistry);
    }

    public void incAlertSuccessCount() {
        alertSuccessCounter.increment();
    }

    public void incAlertFailCount() {
        alertFailCounter.increment();
    }

    public void incAlertHeartbeatCount() {
        alertHeartBeatCounter.increment();
    }
}
