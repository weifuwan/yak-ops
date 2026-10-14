package io.yak.ops.dao.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Verifies the fresh baseline against the complete versioned schema on isolated MySQL databases. */
class FlywayBaselineIntegrationTest {
    private static final String LOCATION = "classpath:db/migration/yak-ops";

    @Test
    @EnabledIfEnvironmentVariable(named = "BASELINE_TEST_DB_URL", matches = ".+")
    void freshBaselineMatchesVersionedMigrationHistory() {
        DriverManagerDataSource baseline = database("BASELINE_TEST_DB_URL");
        DriverManagerDataSource history = database("HISTORY_TEST_DB_URL");

        Flyway fresh = Flyway.configure().dataSource(baseline).locations(LOCATION).load();
        fresh.migrate();
        fresh.validate();

        Flyway firstEleven = Flyway.configure()
                .dataSource(history)
                .locations(LOCATION)
                .target("11")
                .load();
        firstEleven.migrate();

        Flyway upgraded = Flyway.configure().dataSource(history).locations(LOCATION).load();
        upgraded.migrate();
        upgraded.validate();

        JdbcTemplate newDatabase = new JdbcTemplate(baseline);
        JdbcTemplate existingDatabase = new JdbcTemplate(history);

        assertEquals(1, count(newDatabase, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
        assertEquals(
                1,
                count(
                        newDatabase,
                        "SELECT COUNT(*) FROM flyway_schema_history "
                                + "WHERE version = '12' AND type = 'SQL_BASELINE' AND success = 1"));
        assertEquals(12, count(existingDatabase, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));

        String tableNamesQuery = "SELECT TABLE_NAME FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' "
                + "AND TABLE_NAME <> 'flyway_schema_history' ORDER BY TABLE_NAME";
        List<String> tableNames = newDatabase.queryForList(tableNamesQuery, String.class);
        assertEquals(23, tableNames.size());
        assertFalse(tableNames.contains("yak_ops_data_sync_task"));
        assertFalse(tableNames.contains("yak_ops_task_attempt"));
        assertEquals(existingDatabase.queryForList(tableNamesQuery, String.class), tableNames);

        assertSchemaEquals(
                newDatabase,
                existingDatabase,
                "SELECT TABLE_NAME, ENGINE, TABLE_COLLATION, TABLE_COMMENT "
                        + "FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history' "
                        + "ORDER BY TABLE_NAME");
        assertSchemaEquals(
                newDatabase,
                existingDatabase,
                "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, "
                        + "EXTRA, GENERATION_EXPRESSION, CHARACTER_SET_NAME, COLLATION_NAME, COLUMN_COMMENT "
                        + "FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history' "
                        + "ORDER BY TABLE_NAME, COLUMN_NAME");
        assertSchemaEquals(
                newDatabase,
                existingDatabase,
                "SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, SUB_PART, "
                        + "COLLATION, INDEX_TYPE FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history' "
                        + "ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX");
        assertSchemaEquals(
                newDatabase,
                existingDatabase,
                "SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, tc.CONSTRAINT_TYPE, cc.CHECK_CLAUSE "
                        + "FROM information_schema.TABLE_CONSTRAINTS tc "
                        + "LEFT JOIN information_schema.CHECK_CONSTRAINTS cc "
                        + "ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA "
                        + "AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME "
                        + "WHERE tc.CONSTRAINT_SCHEMA = DATABASE() "
                        + "AND tc.TABLE_NAME <> 'flyway_schema_history' "
                        + "ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME");
    }

    private static DriverManagerDataSource database(String environmentName) {
        String url = System.getenv(environmentName);
        String password = System.getenv("E2E_DB_PASSWORD");
        if (url == null || url.isBlank() || password == null) {
            throw new IllegalStateException("MySQL migration acceptance requires both database URLs and password");
        }
        return new DriverManagerDataSource(url, "root", password);
    }

    private static int count(JdbcTemplate database, String statement) {
        return database.queryForObject(statement, Integer.class);
    }

    private static void assertSchemaEquals(JdbcTemplate fresh, JdbcTemplate historical, String statement) {
        List<Map<String, Object>> newSchema = fresh.queryForList(statement);
        assertEquals(historical.queryForList(statement), newSchema, statement);
    }
}
