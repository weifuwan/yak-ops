package io.yak.ops.connector.jdbc.database.catalog;

import java.util.Objects;

/**
 * Physical JDBC column metadata for browsing and preserving vendor-specific display details.
 *
 * <p>Source/Sink type decisions must use JdbcCatalog.getTable(TableId) and its dialect
 * converter, never re-map jdbcType or typeName in the product Business layer.
 *
 * @param name actual column identifier
 * @param typeName JDBC vendor type name
 * @param jdbcType value from java.sql.Types
 * @param size column capacity or precision; nullable when unknown
 * @param scale fractional precision; nullable when unknown
 * @param nullable whether the column can contain SQL NULL
 * @param ordinalPosition physical column order
 * @param primaryKey whether the column belongs to the primary key
 * @param primaryKeyPosition stable KEY_SEQ order, null for non-key fields
 * @param remarks optional column comment
 */
public record JdbcColumnInfo(
        String name,
        String typeName,
        int jdbcType,
        Integer size,
        Integer scale,
        boolean nullable,
        int ordinalPosition,
        boolean primaryKey,
        Integer primaryKeyPosition,
        String remarks) {

    public JdbcColumnInfo {
        Objects.requireNonNull(name, "name");
    }
}
