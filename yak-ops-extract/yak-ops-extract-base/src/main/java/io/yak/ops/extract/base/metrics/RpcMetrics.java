package io.yak.ops.extract.base.metrics;

import io.yak.ops.extract.base.protocal.Transporter;
import io.yak.ops.extract.base.protocal.TransporterHeader;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

public class RpcMetrics {

    private static final Map<String, Timer> rpcRequestDurationTimer = new ConcurrentHashMap<>();

    private static final Map<String, Counter> rpcRequestExceptionCounter = new ConcurrentHashMap<>();

    public static void recordClientSyncRequestException(ClientSyncExceptionMetrics clientSyncExceptionMetrics) {
        recordClientSyncRequestException(
                clientSyncExceptionMetrics.getThrowable(),
                Optional.of(clientSyncExceptionMetrics)
                        .map(ClientSyncExceptionMetrics::getTransporter)
                        .map(Transporter::getHeader)
                        .map(TransporterHeader::getMethodIdentifier)
                        .orElse("unknown"),
                clientSyncExceptionMetrics.getClientHost(),
                clientSyncExceptionMetrics.getServerAddress());
    }

    public static void recordClientSyncRequestException(final Throwable throwable,
                                                        final String methodName,
                                                        final String clientHost,
                                                        final String serverHost) {
        final String exceptionType = throwable == null ? "unknown" : throwable.getClass().getSimpleName();
        final Counter counter = rpcRequestExceptionCounter.computeIfAbsent(exceptionType,
                (et) -> Counter.builder("ds.rpc.client.sync.request.exception.count")
                        .tag("method_name", methodName)
                        .tag("client_host", clientHost)
                        .tag("server_host", serverHost)
                        .tag("exception_name", et)
                        .description("rpc sync request exception counter for exception type: " + et)
                        .register(Metrics.globalRegistry));
        counter.increment();
    }

    public static void recordClientSyncRequestDuration(ClientSyncDurationMetrics clientSyncDurationMetrics) {
        recordClientSyncRequestDuration(
                Optional.of(clientSyncDurationMetrics)
                        .map(ClientSyncDurationMetrics::getTransporter)
                        .map(Transporter::getHeader)
                        .map(TransporterHeader::getMethodIdentifier)
                        .orElseGet(() -> "unknown"),
                clientSyncDurationMetrics.getMilliseconds(),
                clientSyncDurationMetrics.getClientHost(),
                clientSyncDurationMetrics.getServerHost());
    }

    public static void recordClientSyncRequestDuration(final String methodName,
                                                       final long milliseconds,
                                                       final String clientHost,
                                                       final String serverHost) {
        rpcRequestDurationTimer.computeIfAbsent(methodName,
                (method) -> Timer.builder("ds.rpc.client.sync.request.duration.time")
                        .tag("method_name", method)
                        .tag("client_host", clientHost)
                        .tag("server_host", serverHost)
                        .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                        .publishPercentileHistogram()
                        .description("time cost of sync rpc request, unit ms")
                        .register(Metrics.globalRegistry))
                .record(milliseconds, TimeUnit.MILLISECONDS);
    }

}
