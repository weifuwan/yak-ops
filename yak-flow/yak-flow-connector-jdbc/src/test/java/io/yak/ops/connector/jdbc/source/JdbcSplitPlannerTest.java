package io.yak.ops.connector.jdbc.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.dialect.AnsiJdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcSplitPlanner;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.TableId;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises actual JDBC metadata and disjoint split planning against an embedded database. */
class JdbcSplitPlannerTest {

    private static final String URL = "jdbc:h2:mem:jdbc_planner;DB_CLOSE_DELAY=-1";
    private final JdbcConnectionOptions connection = new JdbcConnectionOptions(URL, "sa", "");

    @BeforeEach
    void createTables() throws Exception {
        try (Connection opened = connection.openConnection();
                Statement sql = opened.createStatement()) {
            sql.execute("DROP TABLE IF EXISTS ORDERS");
            sql.execute("DROP TABLE IF EXISTS NO_KEY");
            sql.execute("DROP TABLE IF EXISTS COMPOSITE_KEY");
            sql.execute("CREATE TABLE ORDERS (ID BIGINT PRIMARY KEY, LABEL VARCHAR(40))");
            sql.execute("CREATE TABLE NO_KEY (CONTENT VARCHAR(40))");
            sql.execute("CREATE TABLE COMPOSITE_KEY (A INT, B INT, PRIMARY KEY (A,B))");
            for (int i = 1; i <= 25; i++) {
                sql.execute("INSERT INTO ORDERS VALUES (" + i + ", 'row" + i + "')");
            }
        }
    }

    @Test
    void numericPrimaryKeySplitsAreDisjointAndCoverAllRows() throws Exception {
        Configuration config = new Configuration();
        config.set(JdbcSourceOptions.TARGET_ROWS_PER_SPLIT, 5);
        List<JdbcSourceSplit> splits = new JdbcSplitPlanner(connection, new AnsiJdbcDialect(), config)
                .plan(new TableId(null, "PUBLIC", "ORDERS"), 2);

        assertEquals(5, splits.size());
        assertEquals(1L, splits.getFirst().lowerBound());
        assertEquals(25L, splits.getLast().upperBound());
        assertEquals(5, splits.stream().map(JdbcSourceSplit::splitId).distinct().count());
        for (int i = 1; i < splits.size(); i++) {
            assertEquals(splits.get(i - 1).upperBound() + 1, splits.get(i).lowerBound());
        }
        assertTrue(splits.stream().allMatch(split -> split.tableId().table().equals("ORDERS")));
        assertEquals(List.of("ID", "LABEL"), splits.getFirst().columns());
    }

    @Test
    void keylessAndCompositeKeyTablesFallBackToOneReplayableSplit() throws Exception {
        JdbcSplitPlanner planner = new JdbcSplitPlanner(connection, new AnsiJdbcDialect(), new Configuration());
        for (String table : List.of("NO_KEY", "COMPOSITE_KEY")) {
            List<JdbcSourceSplit> splits = planner.plan(new TableId(null, "PUBLIC", table), 0);
            assertEquals(1, splits.size());
            assertNull(splits.getFirst().splitColumn());
            assertNull(splits.getFirst().lastEmittedKey());
        }
    }

    @Test
    void missingTableFailsInsteadOfSilentlyReturningNoData() {
        JdbcSplitPlanner planner = new JdbcSplitPlanner(connection, new AnsiJdbcDialect(), new Configuration());
        assertThrows(Exception.class, () -> planner.plan(new TableId(null, "PUBLIC", "MISSING"), 0));
    }

    @Test
    void dialectQuotesNamesAndRejectsUnsupportedUrls() {
        assertEquals("`a``b`", JdbcDialects.forUrl("jdbc:mysql://localhost/test").quoteIdentifier("a`b"));
        assertEquals("\"a\"\"b\"", JdbcDialects.forUrl("jdbc:postgresql://localhost/test").quoteIdentifier("a\"b"));
        assertEquals("\"TEST\"", JdbcDialects.forUrl("jdbc:oracle:thin:@localhost:1521/X").quoteIdentifier("TEST"));
        assertThrows(IllegalStateException.class, () -> JdbcDialects.forUrl("jdbc:sqlserver://localhost"));
    }
}
