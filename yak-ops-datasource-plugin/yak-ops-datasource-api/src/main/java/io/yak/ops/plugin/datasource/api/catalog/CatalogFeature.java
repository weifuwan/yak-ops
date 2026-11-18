package io.yak.ops.plugin.datasource.api.catalog;

/** Optional metadata features implemented by a datasource catalog plugin. */
public enum CatalogFeature {
    COLUMNS,
    INDEXES,
    PRIMARY_KEYS,
    FOREIGN_KEYS,
    PARTITIONS,
    DDL,
    TABLE_STATISTICS,
    COMMENTS
}
