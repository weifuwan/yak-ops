package io.yak.ops.cdc.runtime.api.metrics;

/** Metrics boundary supplied by the standalone runtime to connectors. */
public interface MetricGroup {
    MetricGroup addGroup(String name);

    Counter counter(String name);

    <T> void gauge(String name, Gauge<T> gauge);
}
