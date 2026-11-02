package io.yak.ops.cdc.runtime.api.metrics;

/** Runtime-owned counter interface for source and sink metrics. */
public interface Counter {
    void inc();

    void inc(long amount);

    long getCount();
}
