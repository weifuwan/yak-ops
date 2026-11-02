package io.yak.ops.cdc.runtime.api.metrics;

/** Flink SourceReader metric capabilities retained for local CDC reader contexts. */
public interface SourceReaderMetricGroup extends MetricGroup {
    Counter getNumRecordsInErrorsCounter();

    void setPendingRecordsGauge(Gauge<Long> gauge);

    void setPendingBytesGauge(Gauge<Long> gauge);
}
