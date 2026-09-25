package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 缓存数据库、Schema、表和字段等稳定 Catalog 元数据，缓存键不包含任何连接密钥。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceCatalogMetadataCache {

    private static final int MAX_ENTRIES = 2048;

    private final ConcurrentMap<CacheKey, CacheEntry<?>> entries = new ConcurrentHashMap<>();

    public <T> T getOrLoad(CacheKey key, int ttlSeconds, Supplier<T> loader) {
        return getOrLoad(key, ttlSeconds, loader, ignored -> {});
    }

    public <T> T getOrLoad(CacheKey key, int ttlSeconds, Supplier<T> loader, Consumer<Boolean> lookupObserver) {
        Objects.requireNonNull(key, "cache key must not be null");
        Objects.requireNonNull(loader, "cache loader must not be null");
        Objects.requireNonNull(lookupObserver, "cache lookup observer must not be null");
        if (ttlSeconds <= 0) {
            lookupObserver.accept(false);
            return loader.get();
        }

        AtomicBoolean hit = new AtomicBoolean(false);
        CacheEntry<?> entry;
        try {
            entry = entries.compute(key, (ignored, current) -> {
                long now = System.nanoTime();
                if (current != null && current.expiresAtNanos() > now) {
                    hit.set(true);
                    return current;
                }
                T value = loader.get();
                return new CacheEntry<>(value, now + TimeUnit.SECONDS.toNanos(Math.max(1L, ttlSeconds)));
            });
        } finally {
            lookupObserver.accept(hit.get());
        }

        trimIfNecessary(System.nanoTime());

        @SuppressWarnings("unchecked")
        T value = (T) entry.value();
        return value;
    }

    public CacheKey key(DataSourceEntity entity, String kind, Object... qualifiers) {
        Objects.requireNonNull(entity, "datasource entity must not be null");
        List<String> normalizedQualifiers = Arrays.stream(qualifiers == null ? new Object[0] : qualifiers)
                .map(value -> value == null ? "" : String.valueOf(value))
                .toList();
        return new CacheKey(
                entity.getId(),
                entity.getUpdateTime(),
                Objects.requireNonNull(kind, "cache kind must not be null"),
                normalizedQualifiers);
    }

    public int invalidate(String dataSourceId) {
        if (dataSourceId == null) return 0;
        int before = entries.size();
        entries.keySet().removeIf(key -> Objects.equals(dataSourceId, key.dataSourceId()));
        return Math.max(0, before - entries.size());
    }

    void clear() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    private void trimIfNecessary(long now) {
        if (entries.size() <= MAX_ENTRIES) return;
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() <= now);
        int excess = entries.size() - MAX_ENTRIES;
        if (excess <= 0) return;
        for (CacheKey key : entries.keySet()) {
            if (excess <= 0) break;
            if (entries.remove(key) != null) excess--;
        }
    }

    /**
     * Catalog 缓存键只保留数据源 ID、更新时间、元数据种类和非敏感限定条件。
     *
     * @param dataSourceId 数据源 ID
     * @param dataSourceUpdateTime 数据源最后更新时间
     * @param kind 元数据种类
     * @param qualifiers database/schema/table 等非敏感限定条件
     * @author weifuwan
     * @since 2026-09-24
     */
    public record CacheKey(
            String dataSourceId, LocalDateTime dataSourceUpdateTime, String kind, List<String> qualifiers) {
        public CacheKey {
            qualifiers = qualifiers == null ? List.of() : List.copyOf(qualifiers);
        }
    }

    /**
     * 缓存值和过期时间的内部不可变包装。
     *
     * @param value 缓存值
     * @param expiresAtNanos 过期时间的 System.nanoTime 基准值
     * @author weifuwan
     * @since 2026-09-24
     */
    private record CacheEntry<T>(T value, long expiresAtNanos) {}
}
