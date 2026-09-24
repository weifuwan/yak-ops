package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
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
 * Small in-process TTL cache for stable datasource catalog metadata.
 *
 * <p>The datasource update timestamp is part of the key, so editing connection configuration naturally
 * moves subsequent reads to a new cache namespace without exposing connection secrets in cache keys.
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

    public CacheKey key(DataSourceDefinition definition, String kind, Object... qualifiers) {
        Objects.requireNonNull(definition, "datasource definition must not be null");
        List<String> normalizedQualifiers = Arrays.stream(qualifiers == null ? new Object[0] : qualifiers)
                .map(value -> value == null ? "" : String.valueOf(value))
                .toList();
        return new CacheKey(
                definition.getId(),
                definition.getUpdateTime(),
                Objects.requireNonNull(kind, "cache kind must not be null"),
                normalizedQualifiers);
    }

    /** Remove all local metadata cache entries for one datasource. */
    public int invalidate(Long dataSourceId) {
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
            if (entries.remove(key) != null) {
                excess--;
            }
        }
    }

    public record CacheKey(
            Long dataSourceId, LocalDateTime dataSourceUpdateTime, String kind, List<String> qualifiers) {

        public CacheKey {
            qualifiers = qualifiers == null ? List.of() : List.copyOf(qualifiers);
        }
    }

    private record CacheEntry<T>(T value, long expiresAtNanos) {}
}
