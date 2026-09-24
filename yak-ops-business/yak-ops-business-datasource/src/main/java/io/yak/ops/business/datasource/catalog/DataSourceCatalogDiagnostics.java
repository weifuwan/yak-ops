package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Lightweight in-process observability for physical datasource Catalog reads. */
@Slf4j
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceCatalogDiagnostics {

    @Resource
    private DataSourceProperties properties;

    private final ConcurrentMap<String, OperationAccumulator> operations = new ConcurrentHashMap<>();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();

    public <T> T observe(DataSourceDefinition definition, String operation, Supplier<T> action) {
        long startedAt = System.nanoTime();
        boolean failed = false;
        try {
            return action.get();
        } catch (RuntimeException | Error exception) {
            failed = true;
            throw exception;
        } finally {
            long durationNanos = Math.max(0L, System.nanoTime() - startedAt);
            record(definition, operation, durationNanos, failed);
        }
    }

    public void recordCacheLookup(boolean hit) {
        if (hit) {
            cacheHits.increment();
        } else {
            cacheMisses.increment();
        }
    }

    public Snapshot snapshot() {
        long hits = cacheHits.sum();
        long misses = cacheMisses.sum();
        long lookups = hits + misses;
        double hitRate = lookups == 0L ? 0D : (double) hits / lookups;
        List<OperationSnapshot> operationSnapshots = operations.entrySet().stream()
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .sorted(Comparator.comparing(OperationSnapshot::operation))
                .toList();
        return new Snapshot(hits, misses, hitRate, operationSnapshots);
    }

    void reset() {
        operations.clear();
        cacheHits.reset();
        cacheMisses.reset();
    }

    private void record(DataSourceDefinition definition, String operation, long durationNanos, boolean failed) {
        String operationName = operation == null || operation.isBlank() ? "unknown" : operation;
        long thresholdMs = Math.max(1L, properties.getCatalog().getSlowOperationThresholdMillis());
        long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
        boolean slow = durationMs >= thresholdMs;

        operations
                .computeIfAbsent(operationName, ignored -> new OperationAccumulator())
                .record(durationNanos, failed, slow, durationMs);

        if (slow) {
            String dbType = definition == null || definition.getDbType() == null
                    ? "UNKNOWN"
                    : definition.getDbType().name();
            Long dataSourceId = definition == null ? null : definition.getId();
            log.warn(
                    "Slow datasource catalog operation operation={} dataSourceId={} dbType={} durationMs={} thresholdMs={} failed={}",
                    operationName,
                    dataSourceId,
                    dbType,
                    durationMs,
                    thresholdMs,
                    failed);
        }
    }

    public record Snapshot(long cacheHits, long cacheMisses, double cacheHitRate, List<OperationSnapshot> operations) {}

    public record OperationSnapshot(
            String operation,
            long total,
            long failures,
            long slow,
            long averageDurationMs,
            long maxDurationMs,
            Long lastSlowDurationMs,
            LocalDateTime lastSlowTime) {}

    private static final class OperationAccumulator {
        private final LongAdder total = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder slow = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();
        private final AtomicLong lastSlowDurationMs = new AtomicLong(-1L);
        private final AtomicReference<LocalDateTime> lastSlowTime = new AtomicReference<>();

        void record(long durationNanos, boolean failed, boolean isSlow, long durationMs) {
            total.increment();
            totalNanos.add(durationNanos);
            maxNanos.accumulateAndGet(durationNanos, Math::max);
            if (failed) failures.increment();
            if (isSlow) {
                slow.increment();
                lastSlowDurationMs.set(durationMs);
                lastSlowTime.set(LocalDateTime.now());
            }
        }

        OperationSnapshot snapshot(String operation) {
            long totalValue = total.sum();
            long averageNanos = totalValue == 0L ? 0L : totalNanos.sum() / totalValue;
            long lastSlow = lastSlowDurationMs.get();
            return new OperationSnapshot(
                    operation,
                    totalValue,
                    failures.sum(),
                    slow.sum(),
                    TimeUnit.NANOSECONDS.toMillis(averageNanos),
                    TimeUnit.NANOSECONDS.toMillis(maxNanos.get()),
                    lastSlow < 0L ? null : lastSlow,
                    lastSlowTime.get());
        }
    }
}
