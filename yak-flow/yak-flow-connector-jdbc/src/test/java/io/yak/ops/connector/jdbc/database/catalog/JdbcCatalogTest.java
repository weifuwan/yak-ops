package io.yak.ops.connector.jdbc.database.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
    void exposesOnePhysicalMetadataSourceForSchemaAndUiDescriptions() throws Exception {
        JdbcConnectionOptions options = new JdbcConnectionOptions("jdbc:h2:mem:yak_catalog_details;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = options.openConnection(); Statement ddl = connection.createStatement()) {
            ddl.execute("CREATE SCHEMA APP");
            ddl.execute("CREATE TABLE APP.PRODUCTS (TENANT INT, ID BIGINT, NAME VARCHAR(50),"
                    + " PRIMARY KEY(ID, TENANT))");
            ddl.execute("COMMENT ON TABLE APP.PRODUCTS IS 'product list'");
            ddl.execute("COMMENT ON COLUMN APP.PRODUCTS.NAME IS 'display name'");
        }

        AtomicInteger opened = new AtomicInteger();
        try (JdbcCatalog catalog = JdbcCatalogFactory.create(options.url(), () -> {
            opened.incrementAndGet();
            return options.openConnection();
        })) {
            var infos = catalog.listTableInfos(null, "APP", "prod", 10);
            assertEquals(1, infos.size());
            assertEquals("PRODUCTS", infos.getFirst().tableId().table());
            assertEquals("product list", infos.getFirst().remarks());
            TableId table = new TableId(null, "APP", "PRODUCTS");
            assertTrue(catalog.findTable(table).isPresent());
            assertTrue(catalog.findTable(table).orElseThrow().type().contains("TABLE"));
            var columns = catalog.getColumns(table);
            assertEquals(List.of("TENANT", "ID", "NAME"), columns.stream()
                    .map(JdbcColumnInfo::name)
                    .toList());
            assertEquals(2, columns.get(0).primaryKeyPosition());
            assertEquals(1, columns.get(1).primaryKeyPosition());
            assertFalse(columns.get(2).primaryKey());
            assertEquals("display name", columns.get(2).remarks());
            assertEquals(List.of("ID", "TENANT"), catalog.getTable(table).primaryKeys());
            assertThrows(IllegalArgumentException.class, () -> catalog.listTableInfos(null, "APP", null, 0));
        }
        assertTrue(opened.get() >= 5, "Connector Catalog must use the injected provider for every metadata query");
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
    void catalogCanBeSerializedWithoutRetainingAnActiveJdbcConnection() throws Exception {
        JdbcConnectionOptions options = new JdbcConnectionOptions("jdbc:h2:mem:yak_catalog_serial", "sa", "");
        byte[] serialized;
        try (JdbcCatalog catalog = JdbcCatalogFactory.create(options);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(buffer)) {
            output.writeObject(catalog);
            output.flush();
            serialized = buffer.toByteArray();
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized));
                JdbcCatalog restored = (JdbcCatalog) input.readObject()) {
            assertTrue(restored.listDatabases().size() >= 1);
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
