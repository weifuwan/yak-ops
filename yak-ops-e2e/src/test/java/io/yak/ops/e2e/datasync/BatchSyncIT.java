package io.yak.ops.e2e.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.e2e.support.BackendTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BatchSyncIT extends BackendTestBase {
    @Test
    @SuppressWarnings("unchecked")
    void sourcePreviewHasNoTaskSideEffectsAndPublishedTableOptionsReachExecution() throws Exception {
        fixture.source.execute("CREATE TABLE items (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)");
        fixture.createTarget("orders");
        fixture.target.execute("INSERT INTO " + fixture.table("orders") + " VALUES (42,'existing')");
        Map<String, Object> parameters = fixture.parameters(
                "SINGLE_TABLE_OFFLINE", List.of("FULL"), List.of(fixture.route("orders", "orders")));
        Map<String, Object> source = (Map<String, Object>) parameters.get("source");
        Map<String, Object> sourceTable = ((List<Map<String, Object>>) source.get("tables")).getFirst();
        sourceTable.put("read", Map.of("where", "id > 1", "splitKey", "id"));
        sourceTable.put("options", Map.of("fetchSize", 32, "queryTimeoutSeconds", 5));
        Map<String, Object> sink = (Map<String, Object>) parameters.get("sink");
        Map<String, Object> targetTable = ((List<Map<String, Object>>) sink.get("tables")).getFirst();
        Map<String, Object> write = (Map<String, Object>) targetTable.get("write");
        write.put("batchSize", 1);
        write.put("preSql", List.of(
                "DELETE FROM " + fixture.table("orders"),
                "INSERT INTO " + fixture.table("orders") + " (id,name) VALUES (99,'prepared')"));
        write.put("postSql", List.of(
                "UPDATE " + fixture.table("orders") + " SET name = 'completed' WHERE id = 2"));
        String definition = fixture.create(parameters, false);
        String definitionPath = "/api/v1/task-definitions/" + definition;
        JsonNode before = fixture.api.call("GET", definitionPath);
        assertEquals(8, before.path("parameterSchemaVersion").asInt());
        String catalog = "/api/v1/data-source/" + source.get("datasourceId") + "/catalog/";

        JsonNode first = fixture.api.call("POST", catalog + "table-page", Map.of(
                "database", fixture.sourceName, "tablePattern", "items|orders", "offset", 0, "limit", 1));
        assertEquals("items", first.path("items").get(0).path("name").asText());
        assertEquals(1, first.path("nextOffset").asInt());
        JsonNode second = fixture.api.call("POST", catalog + "table-page", Map.of(
                "database", fixture.sourceName, "tablePattern", "items|orders", "offset", 1, "limit", 1));
        assertEquals("orders", second.path("items").get(0).path("name").asText());
        assertTrue(second.path("nextOffset").isNull());

        // An unsaved predicate must be used by preview, not the definition's id > 1.
        Map<String, Object> previewRequest = Map.of(
                "database", fixture.sourceName, "table", "orders", "where", "id = 1", "limit", 20);
        JsonNode preview = fixture.api.call("POST", catalog + "preview", previewRequest);
        assertEquals(1, preview.path("rows").size());
        assertTrue(preview.path("rows").get(0).get(0).isTextual());
        assertEquals("1", preview.path("rows").get(0).get(0).asText());
        assertEquals("one", preview.path("rows").get(0).get(1).asText());
        JsonNode after = fixture.api.call("GET", definitionPath);
        assertEquals(before.path("revision"), after.path("revision"));
        assertEquals(before.path("definitionVersion"), after.path("definitionVersion"));
        assertFalse(after.hasNonNull("publishedVersion"));
        fixture.assertTarget("orders", List.of(List.of(42L, "existing")));

        String workspace = fixture.api.workspace();
        String other = fixture.api.call("POST", "/api/v1/workspaces", Map.of("name", "preview-isolation-" + definition))
                .path("id").asText();
        try {
            fixture.api.workspace(other);
            fixture.api.expect("POST", catalog + "preview", previewRequest, 404);
            fixture.api.expect("POST", catalog + "table-page", Map.of("limit", 1), 404);
        } finally {
            fixture.api.workspace(workspace);
        }

        fixture.api.publish(definition);
        String instance = fixture.submit(definition);
        fixture.awaitState(instance, "FINISHED");
        fixture.assertTarget("orders", List.of(List.of(2L, "completed"), List.of(99L, "prepared")));
        assertEquals(0, fixture.claimCount(instance));
    }

    @Test
    void oneTaskTransfersTwoIndependentTables() throws Exception {
        fixture.source.execute("CREATE TABLE items (id BIGINT PRIMARY KEY,name VARCHAR(32) NOT NULL)");
        fixture.source.execute("INSERT INTO items VALUES(10,'item')");
        fixture.createTarget("orders");
        fixture.createTarget("items");
        String definition = fixture.create(
                fixture.parameters(
                        "DATABASE_OFFLINE",
                        List.of("FULL"),
                        List.of(fixture.route("orders", "orders"), fixture.route("items", "items"))),
                true);
        String instance = fixture.submit(definition);
        fixture.awaitState(instance, "FINISHED");
        fixture.assertTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
        fixture.assertTarget("items", List.of(List.of(10L, "item")));
        assertEquals(0, fixture.claimCount(instance));
    }

    @Test
    void writeFailureIsVisibleAndDoesNotLeaveClaims() throws Exception {
        String definition = fixture.batch();
        fixture.target.execute("ALTER TABLE " + fixture.table("orders") + " ADD CONSTRAINT reject_rows CHECK (id < 0)");
        String instance = fixture.submit(definition);
        fixture.awaitState(instance, "FAILED");
        assertEquals(0, fixture.claimCount(instance));
        fixture.assertTarget("orders", List.of());
    }
}
