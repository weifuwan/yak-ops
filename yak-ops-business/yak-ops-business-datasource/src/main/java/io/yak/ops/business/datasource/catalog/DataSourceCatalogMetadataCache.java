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

    public <T> T getOrLoad(
            DataSourceEntity entity,
            String kind,
            int ttlSeconds,
            Supplier<T> loader,
            Object... qualifiers) {
        Objects.requireNonNull(entity, "datasource entity must not be null");
        Objects.requireNonNull(kind, "cache kind must not be null");
        Objects.requireNonNull(loader, "cache loader must not be null");
        if (ttlSeconds <= 0) return loader.get();

        CacheKey key = cacheKey(entity, kind, qualifiers);
        CacheEntry<?> entry = entries.compute(key, (ignored, current) -> {
            long now = System.nanoTime();
            if (current != null && current.expiresAtNanos() > now) return current;
            T value = loader.get();
            return new CacheEntry<>(value, now + TimeUnit.SECONDS.toNanos(Math.max(1L, ttlSeconds)));
        });

        trimIfNecessary(System.nanoTime());

        @SuppressWarnings("unchecked")
        T value = (T) entry.value();
        return value;
    }

    public int invalidate(String dataSourceId) {
        if (dataSourceId == null) return 0;
        int before = entries.size();
        entries.keySet().removeIf(key -> Objects.equals(dataSourceId, key.dataSourceId()));
        return Math.max(0, before - entries.size());
    }

    private CacheKey cacheKey(DataSourceEntity entity, String kind, Object... qualifiers) {
        List<String> normalizedQualifiers = Arrays.stream(qualifiers == null ? new Object[0] : qualifiers)
                .map(value -> value == null ? "" : String.valueOf(value))
                .toList();
        return new CacheKey(entity.getId(), entity.getUpdateTime(), kind, normalizedQualifiers);
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

    private record CacheKey(
            String dataSourceId, LocalDateTime dataSourceUpdateTime, String kind, List<String> qualifiers) {
        private CacheKey {
            qualifiers = qualifiers == null ? List.of() : List.copyOf(qualifiers);
        }
    }

    private record CacheEntry<T>(T value, long expiresAtNanos) {}
}
