package io.yak.ops.e2e.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.e2e.support.BackendTestBase;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OfflineIncrementalIT extends BackendTestBase {
    private String definition() throws Exception {
        fixture.createTarget("orders");
        Map<String, Object> route = new LinkedHashMap<>(fixture.route("orders", "orders"));
        route.put("incremental", Map.of("mode", "INCREASING", "column", "id", "initialValue", "0"));
        return fixture.create(
                fixture.parameters("SINGLE_TABLE_OFFLINE", List.of("FULL", "INCREMENTAL"), List.of(route)), true);
    }

    @Test
    void initialLoadNextWindowAndEmptyWindowUseBoundedExecutions() throws Exception {
        String definition = definition();
        fixture.awaitState(fixture.submit(definition), "FINISHED");
        fixture.source.execute("INSERT INTO orders VALUES(3,'three')");
        fixture.awaitState(fixture.submit(definition), "FINISHED");
        fixture.awaitState(fixture.submit(definition), "FINISHED");
        fixture.assertTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two"), List.of(3L, "three")));
    }

    @Test
    void changedTransformCannotReuseCommittedCursorAcrossDefinitionVersions() throws Exception {
        fixture.createTarget("orders");
        Map<String, Object> route = new LinkedHashMap<>(fixture.route("orders", "orders"));
        route.put("source", Map.of("database", fixture.sourceName, "table", "orders"));
        route.put("incremental", Map.of("mode", "INCREASING", "column", "id", "initialValue", "0"));
        Map<String, Object> parameters = fixture.parameters(
                "SINGLE_TABLE_OFFLINE", List.of("FULL", "INCREMENTAL"), List.of(route));
        parameters.put("transform", List.of(Map.of(
                "source-table", fixture.sourceName + ".orders", "projection", "id, name", "filter", "id < 2")));
        String definition = fixture.create(parameters, true);
        String initial = fixture.submit(definition);
        fixture.awaitState(initial, "FINISHED");
        fixture.assertTarget("orders", List.of(List.of(1L, "one")));
        assertEquals("2", committedCursor(definition));

        var saved = fixture.api.call("GET", "/api/v1/task-definitions/" + definition);
        parameters.put("transform", List.of(Map.of(
                "source-table", fixture.sourceName + ".orders", "projection", "id, name", "filter", "id < 4")));
        var updated = fixture.api.call("PUT", "/api/v1/task-definitions/" + definition,
                Map.of("name", "filtered-incremental", "debug", true, "parameters", parameters,
                        "expectedRevision", saved.path("revision").asLong()));
        fixture.api.publish(definition);
        fixture.source.execute("INSERT INTO orders VALUES(3,'three')");
        String changed = fixture.submit(definition);
        fixture.awaitState(changed, "FAILED");
        assertEquals(updated.path("definitionVersion").asInt(),
                fixture.api.call("GET", "/api/v1/task-instances/" + changed + "/definition")
                        .path("version").asInt());
        fixture.assertTarget("orders", List.of(List.of(1L, "one")));
        assertEquals("2", committedCursor(definition));
        assertEquals(0, fixture.claimCount(changed));

        // Explicit initialization under a new definition includes the newly eligible history.
        fixture.awaitState(fixture.submit(fixture.create(parameters, true)), "FINISHED");
        fixture.assertTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two"), List.of(3L, "three")));
    }

    private String committedCursor(String definition) throws Exception {
        try (Connection connection = fixture.app.metadata().connect();
                var statement = connection.prepareStatement(
                        "SELECT cursor_value,initialized,pending_value FROM yak_ops_task_cursor "
                                + "WHERE workspace_id=? AND definition_id=?")) {
            statement.setString(1, fixture.api.workspace());
            statement.setString(2, definition);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next(), "Incremental definition has a committed table cursor");
                String cursor = rows.getString(1);
                assertTrue(rows.getBoolean(2));
                assertNull(rows.getString(3), "A rejected definition must not stage a new window");
                assertFalse(rows.next());
                return cursor;
            }
        }
    }

    @Test
    void failedWindowDoesNotAdvanceCursorAndRetryReusesIt() throws Exception {
        String definition = definition();
        fixture.awaitState(fixture.submit(definition), "FINISHED");
        fixture.target.execute("ALTER TABLE " + fixture.table("orders") + " ADD CONSTRAINT reject_new CHECK(id < 3)");
        fixture.source.execute("INSERT INTO orders VALUES(3,'three')");
        String failed = fixture.submit(definition);
        fixture.awaitState(failed, "FAILED");
        fixture.assertTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
        assertEquals(0, fixture.claimCount(failed));
        fixture.target.execute("ALTER TABLE " + fixture.table("orders") + " DROP CONSTRAINT reject_new");
        fixture.source.execute("INSERT INTO orders VALUES(4,'four')");
        fixture.awaitState(fixture.submit(definition), "FINISHED");
        fixture.assertTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two"), List.of(3L, "three")));
        fixture.awaitState(fixture.submit(definition), "FINISHED");
        fixture.assertTarget(
                "orders", List.of(List.of(1L, "one"), List.of(2L, "two"), List.of(3L, "three"), List.of(4L, "four")));
    }
}
