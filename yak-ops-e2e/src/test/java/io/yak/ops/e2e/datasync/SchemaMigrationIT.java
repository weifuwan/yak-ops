package io.yak.ops.e2e.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.enums.task.DefinitionErrorCode;
import io.yak.ops.e2e.support.BackendTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaMigrationIT extends BackendTestBase {
    @Test
    void publicationDoesNotCreateTablesButExecutionDoes() throws Exception {
        var parameters = fixture.parameters(
                "SINGLE_TABLE_OFFLINE", List.of("SCHEMA", "FULL"), List.of(fixture.route("orders", "created_orders")));
        String definition = fixture.create(parameters, false);
        fixture.api.publish(definition);
        try (var connection = fixture.target.connect();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT to_regclass('" + fixture.table("created_orders") + "')")) {
            assertTrue(rows.next());
            assertEquals(null, rows.getObject(1));
        }
        String instance = fixture.submit(definition);
        fixture.awaitState(instance, "FINISHED");
        fixture.assertTarget("created_orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
    }

    @Test
    void incompatibleExistingTargetFailsWithoutPartialData() throws Exception {
        fixture.target.execute("CREATE TABLE " + fixture.table("bad_orders") + " (id BIGINT PRIMARY KEY)");
        var parameters = fixture.parameters(
                "SINGLE_TABLE_OFFLINE", List.of("SCHEMA", "FULL"), List.of(fixture.route("orders", "bad_orders")));
        // The invalid target is rejected before publication, not after a Job has started.
        String definition = fixture.create(parameters, false);
        fixture.api.expectError(
                "POST",
                "/api/v1/task-definitions/" + definition + "/publish",
                fixture.api.publicationRequest(definition),
                400,
                DefinitionErrorCode.PUBLICATION_INVALID.getCode());
        var saved = fixture.api.call("GET", "/api/v1/task-definitions/" + definition);
        assertEquals("UNPUBLISHED", saved.path("status").asText());
        assertFalse(saved.hasNonNull("publishedVersion"));
        assertEquals(0, fixture.api.call("GET", "/api/v1/task-definitions/" + definition + "/publications")
                .path("pagination").path("total").asInt());
        try (var connection = fixture.target.connect();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM " + fixture.table("bad_orders"))) {
            assertTrue(rows.next());
            assertEquals(0, rows.getLong(1));
        }
    }
}
