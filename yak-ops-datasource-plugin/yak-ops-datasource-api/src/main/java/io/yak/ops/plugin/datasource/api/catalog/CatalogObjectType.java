package io.yak.ops.plugin.datasource.api.catalog;

/** Database object types understood by the unified catalog contract. */
public enum CatalogObjectType {
    TABLE,
    VIEW,
    MATERIALIZED_VIEW,
    FUNCTION,
    PROCEDURE,
    TRIGGER,
    SEQUENCE
}
