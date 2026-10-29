package io.yak.ops.dao.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Checks the v1.3.0 fresh-install schema rather than removed historical migrations. */
class TaskExecutionMigrationTest {
    private static final String BASELINE = "/db/migration/yak-ops/V1__baseline.sql";

    @Test
    void createsOnlyFinalTablesWithoutDataMutations() throws IOException {
        String sql = baseline();
        assertEquals(29, Pattern.compile("(?m)^CREATE TABLE ").matcher(sql).results().count());
        assertFalse(Pattern.compile("(?im)^\\s*(UPDATE|INSERT|DELETE|DROP|ALTER|TRUNCATE|REPLACE)\\s+")
                .matcher(sql)
                .find());
        assertFalse(sql.contains("t_ds_"), "Legacy DolphinScheduler table names must not appear in Yak Ops DDL");
        assertFalse(sql.contains("CREATE TABLE yak_ops_task_attempt"));
        assertFalse(sql.contains("CREATE TABLE yak_ops_data_sync_task"));
        assertFalse(sql.contains("CREATE TABLE yak_ops_data_sync_instance"));
    }

    @Test
    void createsCompleteDefinitionInstanceAndResourceTables() throws IOException {
        String sql = baseline();
        for (String expected : new String[] {
            "CREATE TABLE yak_ops_task_definition (",
            "CREATE TABLE yak_ops_task_definition_version (",
            "CREATE TABLE yak_ops_task_instance (",
            "CREATE TABLE yak_ops_task_group (",
            "CREATE TABLE yak_ops_task_group_queue (",
            "CREATE TABLE `yak_ops_jdbc_registry_data`",
            "CREATE TABLE `yak_ops_jdbc_registry_lock`",
            "CREATE TABLE `yak_ops_jdbc_registry_client_heartbeat`",
            "CREATE TABLE `yak_ops_jdbc_registry_data_change_event`",
            "definition_name VARCHAR(128)",
            "debug TINYINT(1) NOT NULL DEFAULT 0",
            "log_path VARCHAR(512)",
            "owner_incarnation VARCHAR(128)",
            "dispatch_token VARCHAR(128)",
            "runtime_engine VARCHAR(32)",
            "runtime_id VARCHAR(128)",
            "progress_percent TINYINT UNSIGNED",
            "execution_report MEDIUMTEXT",
            "uk_ops_task_instance_request",
            "uk_ops_task_group_resource",
            "idx_ops_task_instance_owner",
            "idx_ops_task_instance_latest",
            "CREATE TABLE yak_ops_task_cursor (",
            "CREATE TABLE yak_ops_task_cursor_batch (",
            "revision BIGINT UNSIGNED NOT NULL DEFAULT 1"
        }) {
            assertTrue(sql.contains(expected), expected);
        }
    }

    private static String baseline() throws IOException {
        try (var resource = TaskExecutionMigrationTest.class.getResourceAsStream(BASELINE)) {
            assertNotNull(resource);
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
