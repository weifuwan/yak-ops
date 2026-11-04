package io.yak.ops.api.metrics;

import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ApiServerMetrics {

    private final Counter apiRequestCounter =
            Counter.builder("ds.api.request.count")
                    .description("Api request count")
                    .register(Metrics.globalRegistry);

    private final Counter apiResponse2xxCounter =
            Counter.builder("ds.api.response.count")
                    .tag("code", "2xx")
                    .description("Api 2xx response count")
                    .register(Metrics.globalRegistry);

    private final Counter apiResponse3xxCounter =
            Counter.builder("ds.api.response.count")
                    .tag("code", "3xx")
                    .description("Api 3xx response count")
                    .register(Metrics.globalRegistry);

    private final Counter apiResponse4xxCounter =
            Counter.builder("ds.api.response.count")
                    .tag("code", "4xx")
                    .description("Api 4xx response count")
                    .register(Metrics.globalRegistry);

    private final Counter apiResponse5xxCounter =
            Counter.builder("ds.api.response.count")
                    .tag("code", "5xx")
                    .description("Api 5xx response count")
                    .register(Metrics.globalRegistry);

    private final DistributionSummary apiResourceUploadSizeDistribution =
            DistributionSummary.builder("ds.api.resource.upload.size")
                    .baseUnit("bytes")
                    .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                    .publishPercentileHistogram()
                    .description("size of upload resource files on api")
                    .register(Metrics.globalRegistry);

    private final DistributionSummary apiResourceDownloadSizeDistribution =
            DistributionSummary.builder("ds.api.resource.download.size")
                    .baseUnit("bytes")
                    .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                    .publishPercentileHistogram()
                    .description("size of download resource files on api")
                    .register(Metrics.globalRegistry);

    public void incApiRequestCount() {
        apiRequestCounter.increment();
    }

    public void incApiResponse2xxCount() {
        apiResponse2xxCounter.increment();
    }

    public void incApiResponse3xxCount() {
        apiResponse3xxCounter.increment();
    }

    public void incApiResponse4xxCount() {
        apiResponse4xxCounter.increment();
    }

    public void incApiResponse5xxCount() {
        apiResponse5xxCounter.increment();
    }

    public void recordApiResourceUploadSize(final long size) {
        apiResourceUploadSizeDistribution.record(size);
    }

    public void recordApiResourceDownloadSize(final long size) {
        apiResourceDownloadSizeDistribution.record(size);
    }

    public void cleanUpApiResponseTimeMetricsByUserId(final int userId) {
        Metrics.globalRegistry.remove(
                Metrics.globalRegistry.timer(
                        "ds.api.response.time",
                        "user.id", String.valueOf(userId)));
    }

    public static void registerUncachedException(final Supplier<Number> supplier) {
        Gauge.builder("ds.api.uncached.exception", supplier)
                .description("number of uncached exception")
                .register(Metrics.globalRegistry);
    }
}
