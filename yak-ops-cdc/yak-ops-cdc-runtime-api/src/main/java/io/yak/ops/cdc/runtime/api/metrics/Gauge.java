package io.yak.ops.cdc.runtime.api.metrics;

/** Gauge interface for runtime metrics. */
@FunctionalInterface
public interface Gauge<T> {
    T getValue();
}
