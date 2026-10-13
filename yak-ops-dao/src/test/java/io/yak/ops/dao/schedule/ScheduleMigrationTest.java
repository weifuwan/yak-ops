package io.yak.ops.dao.schedule;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Checks migration contract; real MySQL constraints are exercised by product acceptance. */
class ScheduleMigrationTest {
    @Test
    void keepsDeletedIdentityAndEnforcesOneLiveTarget() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/yak-ops/V10__schedule_definition.sql")) {
            assertNotNull(stream);
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("UNIQUE KEY uk_ops_schedule_target (workspace_id, target_type, active_target_id)"));
            assertTrue(sql.contains("CASE WHEN deleted_at IS NULL THEN target_id ELSE NULL END"));
            assertTrue(sql.contains("DEFAULT 0 COMMENT"));
            assertTrue(sql.contains("ck_ops_schedule_authority"));
            for (String line : sql.split("\\R")) {
                if (line.matches("\\s*[a-z][a-z0-9_]*\\s+(VARCHAR|BIGINT|DATETIME|TINYINT).*")) {
                    if (!line.contains("GENERATED")) {
                        assertTrue(line.contains(" COMMENT '"), line);
                    }
                }
            }
        }
    }
}
