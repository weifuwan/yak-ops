package io.yak.ops.dao.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The forward migration preserves historical snapshots and does not invent publication audit history. */
class TaskPublicationMigrationTest {
    @Test
    void onlyCurrentPublishedStateIsBackfilled() throws IOException {
        String sql;
        try (var resource = getClass().getResourceAsStream("/db/migration/yak-ops/V2__v1_4_0.sql")) {
            assertNotNull(resource);
            sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(sql.contains("SET published_version = current_version, published_debug = debug"));
        assertTrue(sql.contains("WHERE status = 1"));
        assertTrue(sql.contains("revision BIGINT NOT NULL DEFAULT 1"));
        assertTrue(sql.contains("UNIQUE KEY uk_ops_task_publication_request (workspace_id, definition_id, request_id)"));
        assertTrue(sql.contains("COLLATE utf8mb4_bin"));
        assertFalse(Pattern.compile("(?i)(UPDATE|ALTER|DELETE FROM)\\s+yak_ops_task_(definition_version|instance)")
                .matcher(sql).find());
        assertFalse(Pattern.compile("(?im)^\\s*(INSERT|REPLACE|DELETE|DROP|TRUNCATE)\\s+").matcher(sql).find());
    }
}
