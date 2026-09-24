package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
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

/**
 * 记录 Catalog 元数据访问的耗时、失败和缓存命中情况，用于定位慢数据源。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Slf4j
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceCatalogDiagnostics {

    @Resource
    private DataSourceProperties properties;

    private final ConcurrentMap<String, OperationAccumulator> operations = new ConcurrentHashMap<>();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();

    public <T> T observe(DataSourceEntity entity, String operation, Supplier<T> action) {
        long startedAt = System.nanoTime();
        boolean failed = false;
        try {
            return action.get();
        } catch (RuntimeException | Error exception) {
            failed = true;
            throw exception;
        } finally {
            record(entity, operation, Math.max(0L, System.nanoTime() - startedAt), failed);
        }
    }

    public void recordCacheLookup(boolean hit) {
        if (hit) cacheHits.increment();
        else cacheMisses.increment();
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

    private void record(DataSourceEntity entity, String operation, long durationNanos, boolean failed) {
        String operationName = operation == null || operation.isBlank() ? "unknown" : operation;
        long thresholdMs = Math.max(1L, properties.getCatalog().getSlowOperationThresholdMillis());
        long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
        boolean slow = durationMs >= thresholdMs;
        operations
                .computeIfAbsent(operationName, ignored -> new OperationAccumulator())
                .record(durationNanos, failed, slow, durationMs);

        if (slow) {
            String dbType = entity == null || entity.getDbType() == null
                    ? "UNKNOWN"
                    : entity.getDbType().name();
            Long dataSourceId = entity == null ? null : entity.getId();
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

    /**
     * Catalog 运行诊断快照。
     *
     * @param cacheHits 缓存命中次数
     * @param cacheMisses 缓存未命中次数
     * @param cacheHitRate 缓存命中率
     * @param operations 各元数据操作统计
     * @author weifuwan
     * @since 2026-09-24
     */
    public record Snapshot(long cacheHits, long cacheMisses, double cacheHitRate, List<OperationSnapshot> operations) {}

    /**
     * 单个 Catalog 操作的聚合统计。
     *
     * @param operation 操作名称
     * @param total 调用总次数
     * @param failures 失败次数
     * @param slow 慢调用次数
     * @param averageDurationMs 平均耗时毫秒
     * @param maxDurationMs 最大耗时毫秒
     * @param lastSlowDurationMs 最近一次慢调用耗时毫秒
     * @param lastSlowTime 最近一次慢调用时间
     * @author weifuwan
     * @since 2026-09-24
     */
    public record OperationSnapshot(
            String operation,
            long total,
            long failures,
            long slow,
            long averageDurationMs,
            long maxDurationMs,
            Long lastSlowDurationMs,
            LocalDateTime lastSlowTime) {}

    /**
     * 线程安全地累计单个 Catalog 操作的运行指标。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
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
