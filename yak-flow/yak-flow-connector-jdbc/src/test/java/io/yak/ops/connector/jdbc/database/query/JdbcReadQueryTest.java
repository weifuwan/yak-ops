package io.yak.ops.connector.jdbc.database.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcReadQueryTest {
    @Test
    void composedOrConditionCannotEscapeTheSplitRange() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:predicate_contract", "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE SAMPLE (ID INT, LABEL VARCHAR(20))");
            statement.execute("INSERT INTO SAMPLE VALUES(1,'--'),(2,'other'),(3,'--')");
            String condition = JdbcReadQuery.whereClause("LABEL = '--' OR ID = 2", List.of("ID >= ?", "ID <= ?"));
            try (var query = connection.prepareStatement("SELECT ID FROM SAMPLE" + condition)) {
                query.setInt(1, 2);
                query.setInt(2, 2);
                try (var result = query.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(2, result.getInt(1));
                    assertTrue(!result.next());
                }
            }
        }
        assertEquals(List.of("LABEL", "ID"), JdbcReadQuery.referencedColumns("LOWER(LABEL) = 'a' OR ID = 2"));
        assertEquals("", JdbcReadQuery.whereClause(null, List.of()));
    }

    @Test
    void unsupportedSqlCannotTurnAReadPredicateIntoAnotherOperation() {
        for (String predicate : List.of("ID > 1; DELETE FROM SAMPLE", "WHERE ID = 1", "ID IN (SELECT ID FROM OTHER)",
                "ID = ?", "ID = :id", "GET_LOCK('x', 1) = 1", "LOAD_FILE('/tmp/file') IS NOT NULL",
                "ID > 0 ORDER BY ID", "@session = 1")) {
            assertThrows(IllegalArgumentException.class, () -> JdbcReadQuery.validateWhere(predicate), predicate);
        }
        assertTrue(JdbcReadQuery.whereClause("ID > 1 -- end of user text", List.of("ID < ?"))
                .endsWith("AND (ID < ?)"));
    }
}
