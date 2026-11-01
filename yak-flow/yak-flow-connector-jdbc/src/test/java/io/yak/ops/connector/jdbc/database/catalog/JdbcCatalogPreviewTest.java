package io.yak.ops.connector.jdbc.database.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.core.data.TableId;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises bounded reads and complete metadata paging through actual JDBC calls. */
class JdbcCatalogPreviewTest {
    @Test
    void metadataPagesAndPatternsReachBeyondTheOldResultLimit() throws Exception {
        JdbcConnectionOptions binding = binding("catalog_pages");
        try (Connection connection = new DefaultJdbcConnectionProvider(binding).getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA APP");
            for (int i = 0; i < 503; i++) {
                statement.execute("CREATE TABLE APP.T_" + String.format("%03d", i) + " (ID INT)");
            }
        }
        try (JdbcCatalog catalog = JdbcCatalogFactory.create(binding)) {
            JdbcTablePage first = catalog.listTablePage(null, "APP", null, null, "T_.*", 0, 500);
            assertEquals(500, first.items().size());
            assertEquals(500, first.nextOffset());
            JdbcTablePage next = catalog.listTablePage(null, "APP", null, null, "T_.*", first.nextOffset(), 500);
            assertEquals(List.of("T_500", "T_501", "T_502"),
                    next.items().stream().map(item -> item.tableId().table()).toList());
            assertNull(next.nextOffset());
            JdbcTablePage filtered = catalog.listTablePage(null, "APP", null, null, "T_50[0-2]", 0, 2);
            assertEquals(List.of("T_500", "T_501"),
                    filtered.items().stream().map(item -> item.tableId().table()).toList());
            assertEquals(2, filtered.nextOffset());
            assertThrows(IllegalArgumentException.class,
                    () -> catalog.listTablePage(null, "APP", null, null, "[", 0, 10));
        }
    }

    @Test
    void previewPreservesPrecisionNullsAndFilterSemanticsWithoutExecutingStatements() throws Exception {
        JdbcConnectionOptions binding = binding("catalog_preview");
        try (Connection connection = new DefaultJdbcConnectionProvider(binding).getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE SAMPLE (ID BIGINT PRIMARY KEY, AMOUNT DECIMAL(38,10), "
                    + "ENABLED BOOLEAN, NAME VARCHAR(5000))");
            statement.execute("INSERT INTO SAMPLE VALUES (9007199254740993, 12345678901234567890.1234567890, TRUE, NULL)");
            statement.execute("INSERT INTO SAMPLE VALUES (2, 2.5, FALSE, 'small')");
        }
        TableId table = new TableId(null, "PUBLIC", "SAMPLE");
        try (JdbcCatalog catalog = JdbcCatalogFactory.create(binding)) {
            JdbcDataPreview preview = catalog.preview(table, "ID = 9007199254740993", 20);
            assertEquals(4, preview.columns().size());
            assertEquals(1, preview.rows().size());
            assertEquals("9007199254740993", preview.rows().getFirst().get(0));
            assertEquals("12345678901234567890.1234567890", preview.rows().getFirst().get(1));
            assertEquals(true, preview.rows().getFirst().get(2));
            assertNull(preview.rows().getFirst().get(3));
            assertFalse(preview.truncated());
            JdbcDataPreview bounded = catalog.preview(table, "ID > 0", 1);
            assertEquals(1, bounded.rows().size());
            assertTrue(bounded.truncated());
            assertTrue(catalog.preview(table, "ID < 0", 20).rows().isEmpty());
            assertThrows(IllegalArgumentException.class,
                    () -> catalog.preview(table, "ID > 0; DELETE FROM SAMPLE", 20));
            assertEquals(2, catalog.preview(table, null, 20).rows().size());
            assertThrows(IllegalArgumentException.class, () -> catalog.preview(table, null, 101));
        }
    }

    @Test
    void oversizedCellsAreBoundedAndReportedAsTruncated() throws Exception {
        JdbcConnectionOptions binding = binding("catalog_preview_cells");
        try (Connection connection = new DefaultJdbcConnectionProvider(binding).getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE SAMPLE (ID INT PRIMARY KEY, CONTENT CLOB)");
            statement.execute("INSERT INTO SAMPLE VALUES (1, REPEAT('x', 5000))");
        }
        try (JdbcCatalog catalog = JdbcCatalogFactory.create(binding)) {
            JdbcDataPreview preview = catalog.preview(new TableId(null, "PUBLIC", "SAMPLE"), null, 20);
            assertTrue(preview.truncated());
            assertEquals(4096, ((String) preview.rows().getFirst().get(1)).length());
        }
    }

    private static JdbcConnectionOptions binding(String name) {
        return new JdbcConnectionOptions("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1", "sa", "");
    }
}
