package org.apache.flink.cdc.connectors.jdbc.dialect;

import org.apache.flink.cdc.common.event.TableId;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcDialectTest {

    @Test
    void quotesIdentifiersAndEscapesEmbeddedQuoteCharacters() {
        JdbcDialect dialect = new JdbcDialect("\"", false);

        assertEquals("\"order\"\"items\"", dialect.quoteIdentifier("order\"items"));
    }

    @Test
    void buildsSchemaQualifiedTableNames() {
        JdbcDialect dialect = new JdbcDialect("\"", false);

        assertEquals(
                "\"inventory\".\"public\".\"orders\"",
                dialect.qualifiedTable(
                        TableId.tableId("inventory", "public", "orders"), null, null));
    }

    @Test
    void mapsCatalogBasedDatabasesToDatabaseAndTableIdentifiers() {
        JdbcDialect dialect = new JdbcDialect("`", true);

        assertTrue(dialect.isCatalogBased());
        assertEquals(
                "`inventory`.`orders`",
                dialect.qualifiedTable(TableId.tableId("inventory", "orders"), null, null));
        assertEquals(
                TableId.tableId("inventory", "orders"),
                dialect.toTableId("inventory", null, "orders"));
    }
}
