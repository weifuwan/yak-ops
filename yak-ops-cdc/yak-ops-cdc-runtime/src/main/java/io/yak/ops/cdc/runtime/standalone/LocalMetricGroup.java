package io.yak.ops.cdc.runtime.standalone;

import io.yak.ops.cdc.runtime.api.metrics.Counter;
import io.yak.ops.cdc.runtime.api.metrics.Gauge;
import io.yak.ops.cdc.runtime.api.metrics.MetricGroup;
import io.yak.ops.cdc.runtime.api.metrics.SourceReaderMetricGroup;
import io.yak.ops.cdc.runtime.api.metrics.SplitEnumeratorMetricGroup;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe metrics supplied to local source and sink contexts. */
final class LocalMetricGroup implements SourceReaderMetricGroup, SplitEnumeratorMetricGroup {

    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Gauge<?>> gauges = new ConcurrentHashMap<>();

    @Override
    public MetricGroup addGroup(String name) {
        return this;
    }

    @Override
    public Counter counter(String name) {
        return counters.computeIfAbsent(name, ignored -> new LocalCounter());
    }

    @Override
    public <T> void gauge(String name, Gauge<T> gauge) {
        gauges.put(name, gauge);
    }

    @Override
    public Counter getNumRecordsInErrorsCounter() {
        return counter("numRecordsInErrors");
    }

    @Override
    public void setPendingRecordsGauge(Gauge<Long> gauge) {
        gauge("pendingRecords", gauge);
    }

    @Override
    public void setPendingBytesGauge(Gauge<Long> gauge) {
        gauge("pendingBytes", gauge);
    }

    private static final class LocalCounter implements Counter {
        private final AtomicLong value = new AtomicLong();

        @Override
        public void inc() {
            inc(1);
        }

        @Override
        public void inc(long amount) {
            value.addAndGet(amount);
        }

        @Override
        public long getCount() {
            return value.get();
        }
    }
}
