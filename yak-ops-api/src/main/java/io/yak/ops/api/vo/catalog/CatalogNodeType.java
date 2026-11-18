package io.yak.ops.api.vo.catalog;

/** UI tree node types; group nodes are virtual and are not database objects. */
public enum CatalogNodeType {
    DATASOURCE,
    CATALOG,
    DATABASE,
    SCHEMA,
    TABLE_GROUP,
    VIEW_GROUP,
    MATERIALIZED_VIEW_GROUP,
    FUNCTION_GROUP,
    PROCEDURE_GROUP,
    TRIGGER_GROUP,
    SEQUENCE_GROUP,
    TABLE,
    VIEW,
    MATERIALIZED_VIEW,
    FUNCTION,
    PROCEDURE,
    TRIGGER,
    SEQUENCE,
    COLUMN_GROUP,
    COLUMN,
    INDEX_GROUP,
    INDEX
}
