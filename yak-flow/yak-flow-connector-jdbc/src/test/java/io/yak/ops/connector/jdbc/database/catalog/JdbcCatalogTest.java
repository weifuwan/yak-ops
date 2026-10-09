package io.yak.ops.connector.jdbc.database.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the real H2 JDBC Catalog and Core schema/compound primary-key behavior. */
class JdbcCatalogTest {

    @Test
    void discoversTablesAndSchemasWithTheFactorySelectedCatalog() throws Exception {
        JdbcConnectionOptions options = new JdbcConnectionOptions("jdbc:h2:mem:yak_catalog;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = options.openConnection(); Statement ddl = connection.createStatement()) {
            ddl.execute("CREATE SCHEMA IF NOT EXISTS APP");
            ddl.execute("CREATE TABLE APP.ORDERS (TENANT INTEGER, ID BIGINT, AMOUNT DECIMAL(12,2), "
                    + "LABEL VARCHAR(80), PRIMARY KEY (ID, TENANT))");
            ddl.execute("CREATE VIEW APP.ORDER_IDS AS SELECT ID FROM APP.ORDERS");
        }
        try (JdbcCatalog catalog = JdbcCatalogFactory.create(options)) {
            assertTrue(catalog.listDatabases().size() >= 1);
            assertTrue(catalog.listSchemas(null).contains("APP"));
            TableId table = new TableId(null, "APP", "ORDERS");
            TableId view = new TableId(null, "APP", "ORDER_IDS");
            List<TableId> tables = catalog.listTables(null, "APP");
            assertTrue(tables.stream().anyMatch(t -> t.schema().equals("APP") && t.table().equals("ORDERS")));
            assertTrue(tables.stream().anyMatch(t -> t.schema().equals("APP") && t.table().equals("ORDER_IDS")));
            assertTrue(catalog.tableExists(table));
            assertFalse(catalog.tableExists(new TableId(null, "APP", "MISSING")));
            assertTrue(catalog.tableExists(view));
            var schema = catalog.getTable(table);
            assertEquals(4, schema.columnCount());
            assertEquals(List.of("ID", "TENANT"), schema.primaryKeys());
            assertEquals(LogicalTypeRoot.DECIMAL, schema.column(2).dataType().getTypeRoot());
            assertEquals("DECIMAL(12, 2)", schema.column(2).dataType().asSerializableString());
            assertEquals(80, schema.column(3).length());
            assertEquals(List.of(), catalog.getTable(view).primaryKeys());
            assertThrows(SQLException.class, () -> catalog.getTable(new TableId(null, "APP", "MISSING")));
        }
    }

    @Test
    void quotedIdentifierPatternsAreEscapedForExactLookup() throws Exception {
        JdbcConnectionOptions options = new JdbcConnectionOptions("jdbc:h2:mem:yak_catalog_escape;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = options.openConnection(); Statement ddl = connection.createStatement()) {
            ddl.execute("CREATE TABLE \"ORDERS_%\" (ID BIGINT PRIMARY KEY)");
            ddl.execute("CREATE TABLE ORDERS_XYZ (ID BIGINT PRIMARY KEY)");
        }
        try (JdbcCatalog catalog = JdbcCatalogFactory.create(options)) {
            assertTrue(catalog.tableExists(new TableId(null, "PUBLIC", "ORDERS_%")));
            assertEquals(1, catalog.getTable(new TableId(null, "PUBLIC", "ORDERS_%")).columnCount());
            assertFalse(catalog.tableExists(new TableId(null, "PUBLIC", "ORDERS_")));
        }
    }

    @Test
    void nullabilityAndColumnTypesUseExistingDialectConverter() throws Exception {
        JdbcConnectionOptions options = new JdbcConnectionOptions("jdbc:h2:mem:yak_catalog_types;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = options.openConnection(); Statement ddl = connection.createStatement()) {
            ddl.execute("CREATE TABLE METADATA_SAMPLE (ID BIGINT NOT NULL PRIMARY KEY, "
                    + "AMOUNT DECIMAL(16,4), CREATED_ON DATE, CONTENT VARCHAR(64))");
        }
        try (JdbcCatalog catalog = JdbcCatalogFactory.create(options)) {
            var schema = catalog.getTable(new TableId(null, "PUBLIC", "METADATA_SAMPLE"));
            assertFalse(schema.column(0).nullable());
            assertTrue(schema.column(1).nullable());
            assertEquals("DECIMAL(16, 4)", schema.column(1).dataType().asSerializableString());
            assertEquals(LogicalTypeRoot.DATE, schema.column(2).dataType().getTypeRoot());
        }
    }
}
