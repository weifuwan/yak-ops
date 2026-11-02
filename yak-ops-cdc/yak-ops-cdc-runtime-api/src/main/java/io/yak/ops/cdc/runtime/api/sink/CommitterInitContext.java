package io.yak.ops.cdc.runtime.api.sink;

import io.yak.ops.cdc.runtime.api.metrics.MetricGroup;

/** Context provided when creating a standalone Sink V2 committer. */
public interface CommitterInitContext {
    MetricGroup metricGroup();
}
