package io.yak.ops.dao.task;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Checks required uniqueness and ownership columns in the append-only Flyway migration. */
class TaskExecutionMigrationTest {

    @Test
    void persistsAdmissionAndDispatchGuards() throws IOException {
        String sql;
        try (var resource =
                getClass().getResourceAsStream("/db/migration/yak-ops/V5__task_execution_foundation.sql")) {
            assertTrue(resource != null);
            sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(sql.contains("uk_ops_task_instance_request"));
        assertTrue(sql.contains("(workspace_id, trigger_type, request_id)"));
        assertTrue(sql.contains("uk_ops_task_claim_resource"));
        assertTrue(sql.contains("(workspace_id, resource_type, resource_key)"));
        assertTrue(sql.contains("owner_incarnation VARCHAR(128)"));
        assertTrue(sql.contains("dispatch_token VARCHAR(128)"));
        assertTrue(sql.contains("yak_ops_task_execution_owner"));
        int documented = 0;
        for (String line : sql.split("\\R")) {
            if (line.matches("\\s*[a-z][a-z0-9_]*\\s+(?:VARCHAR|INT|DATETIME).*")) {
                assertTrue(line.contains(" COMMENT '"), "Missing column COMMENT: " + line);
                documented++;
            }
        }
        assertTrue(documented >= 50, "Expected execution columns to be documented");
    }
}
