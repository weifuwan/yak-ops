package io.debezium.relational;

import org.apache.flink.shaded.guava31.com.google.common.cache.CacheBuilder;
import org.apache.flink.shaded.guava31.com.google.common.cache.CacheLoader;
import org.apache.flink.shaded.guava31.com.google.common.cache.LoadingCache;

import io.debezium.relational.Tables.TableFilter;

import static org.apache.flink.util.Preconditions.checkNotNull;

/** A bounded cache for table filter results. */
public class CachedTableFilter implements TableFilter {

    private static final long TABLE_FILTER_CACHE_MAXIMUM_SIZE = 32 * 1024;

    private final TableFilter rawTableFilter;
    private final LoadingCache<TableId, Boolean> tableFilterCache;

    private CachedTableFilter(TableFilter rawTableFilter) {
        this.rawTableFilter = rawTableFilter;
        this.tableFilterCache =
                CacheBuilder.newBuilder()
                        .maximumSize(TABLE_FILTER_CACHE_MAXIMUM_SIZE)
                        .build(
                                new CacheLoader<TableId, Boolean>() {
                                    @Override
                                    public Boolean load(TableId tableId) {
                                        return rawTableFilter.isIncluded(tableId);
                                    }
                                });
    }

    /** Wraps the given filter in a cache, or returns it unchanged if it is already cached. */
    public static CachedTableFilter from(TableFilter tableFilter) {
        checkNotNull(tableFilter);
        if (tableFilter instanceof CachedTableFilter) {
            return (CachedTableFilter) tableFilter;
        }
        return new CachedTableFilter(tableFilter);
    }

    /** Returns a new cached filter that also requires the additional filter to match. */
    public CachedTableFilter withAdditionalFilter(TableFilter additionalFilter) {
        checkNotNull(additionalFilter);
        TableFilter rawFilter = rawTableFilter;
        return new CachedTableFilter(
                tableId -> rawFilter.isIncluded(tableId) && additionalFilter.isIncluded(tableId));
    }

    @Override
    public boolean isIncluded(TableId tableId) {
        return tableFilterCache.getUnchecked(tableId);
    }
}
