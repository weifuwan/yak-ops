package io.yak.ops.e2e.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.enums.task.DefinitionErrorCode;
import io.yak.ops.e2e.support.BackendTestBase;
import io.yak.ops.e2e.support.BootProcess;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskExecutionIT extends BackendTestBase {
    @Test
    void submittedBatchCompletesAndReleasesClaims() throws Exception {
        String definition = fixture.batch();
        String instance = fixture.submit(definition);
        var result = fixture.awaitState(instance, "FINISHED");
        assertEquals(definition, result.path("definitionId").asText());
        assertEquals("MANUAL", result.path("triggerType").asText());
        assertEquals("YAKFLOW", result.path("runtimeEngine").asText());
        assertEquals(100, result.path("progressPercent").asInt());
        fixture.assertTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
        assertEquals(0, fixture.claimCount(instance));
    }

    @Test
    void repeatedSubmissionKeyReturnsTheSameInstance() throws Exception {
        String definition = fixture.batch();
        String key = UUID.randomUUID().toString();
        var first = fixture.api.submit(definition, key);
        String instance = first.path("instanceId").asText();
        fixture.track(instance);
        var repeated = fixture.api.submit(definition, key);
        assertFalse(first.path("replayed").asBoolean());
        assertTrue(repeated.path("replayed").asBoolean());
        assertEquals(instance, repeated.path("instanceId").asText());
        fixture.awaitState(instance, "FINISHED");
    }

    @Test
    void otherWorkspaceCannotReadInstanceOrLogs() throws Exception {
        String definition = fixture.batch();
        String instance = fixture.submit(definition);
        fixture.awaitState(instance, "FINISHED");
        String own = fixture.api.workspace();
        String other = fixture.api
                .call("POST", "/api/v1/workspaces", Map.of("name", "other-" + UUID.randomUUID()))
                .path("id")
                .asText();
        try {
            fixture.api.workspace(other);
            fixture.api.expect("GET", "/api/v1/task-instances/" + instance, null, 404);
            fixture.api.expect("GET", "/api/v1/task-instances/" + instance + "/logs", null, 404);
        } finally {
            fixture.api.workspace(own);
        }
    }

    @Test
    void logsContainExecutionEventsWithoutCredentials() throws Exception {
        String instance = fixture.submit(fixture.batch());
        fixture.awaitState(instance, "FINISHED");
        org.awaitility.Awaitility.await()
                .alias("Execution log flush")
                .atMost(java.time.Duration.ofSeconds(30))
                .until(() -> fixture.api
                        .download("/api/v1/task-instances/" + instance + "/logs/download")
                        .contains("JDBC sink summary"));
        String text = fixture.api.download("/api/v1/task-instances/" + instance + "/logs/download");
        for (String event : List.of(
                "YakFlow Job started",
                "JDBC query executed",
                "JDBC fetch completed",
                "JDBC statement batch executed",
                "JDBC flush completed",
                "Source summary",
                "JDBC sink summary")) {
            assertTrue(text.contains(event), "Missing log event: " + event);
        }
        assertFalse(text.contains(BootProcess.PASSWORD));
        assertFalse(text.contains(fixture.source.password()));
        fixture.api.expect("GET", "/api/v1/task-instances/" + instance + "/logs?cursor=bad!!", null, 400);
    }

    @Test
    void historicalInstanceKeepsItsPublishedDefinitionVersion() throws Exception {
        String definition = fixture.batch();
        var saved = fixture.api.call("GET", "/api/v1/task-definitions/" + definition);
        String instance = fixture.submit(definition);
        fixture.awaitState(instance, "FINISHED");
        var old = fixture.api.call("GET", "/api/v1/task-instances/" + instance + "/definition");
        fixture.createTarget("changed_orders");
        var replacement = fixture.parameters(
                "SINGLE_TABLE_OFFLINE", List.of("FULL"), List.of(fixture.route("orders", "changed_orders")));
        var updated = fixture.api.call(
                "PUT",
                "/api/v1/task-definitions/" + definition,
                Map.of(
                        "name", "renamed-task",
                        "debug", false,
                        "parameters", replacement,
                        "expectedRevision", saved.path("revision").asLong()));
        assertTrue(updated.path("definitionVersion").asInt() > old.path("version").asInt());
        assertEquals("PUBLISHED", updated.path("status").asText());
        assertEquals(old.path("version").asInt(), updated.path("publishedVersion").asInt());
        assertTrue(updated.path("publishedDebug").asBoolean());
        assertFalse(updated.path("debug").asBoolean());
        assertTrue(updated.path("hasUnpublishedChanges").asBoolean());

        String beforePublication = fixture.submit(definition);
        var stillPublished = fixture.awaitState(beforePublication, "FINISHED");
        assertEquals(old.path("version").asInt(), stillPublished.path("definitionVersion").asInt());
        assertTrue(stillPublished.path("debug").asBoolean());
        assertEquals(old.path("parameters"), fixture.api.call(
                "GET", "/api/v1/task-instances/" + beforePublication + "/definition").path("parameters"));
        fixture.assertTarget("changed_orders", List.of());

        var published = fixture.api.publish(definition);
        assertEquals(updated.path("definitionVersion").asInt(), published.path("publishedVersion").asInt());
        assertFalse(published.path("publishedDebug").asBoolean());
        assertFalse(published.path("hasUnpublishedChanges").asBoolean());
        String next = fixture.submit(definition);
        var nextInstance = fixture.awaitState(next, "FINISHED");
        assertFalse(nextInstance.path("debug").asBoolean());
        var retained = fixture.api.call("GET", "/api/v1/task-instances/" + instance + "/definition");
        assertEquals(old, retained);
        assertEquals(
                updated.path("definitionVersion").asInt(),
                fixture.api.call("GET", "/api/v1/task-instances/" + next + "/definition")
                        .path("version").asInt());
        fixture.assertTarget("changed_orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
    }

    @Test
    void publicationRetryAndStaleEditorsCannotOverwriteNewerSavedState() throws Exception {
        String definition = fixture.batch();
        String path = "/api/v1/task-definitions/" + definition;
        long previousPublications = fixture.api.call("GET", path + "/publications")
                .path("pagination").path("total").asLong();
        Map<String, Object> request = fixture.api.publicationRequest(definition);
        var published = fixture.api.call("POST", path + "/publish", request);
        assertEquals(published, fixture.api.call("POST", path + "/publish", request));
        assertEquals(previousPublications + 1, fixture.api.call("GET", path + "/publications")
                .path("pagination").path("total").asLong());

        var edited = fixture.api.call("PUT", path, Map.of(
                "name", "saved-after-publication",
                "debug", false,
                "expectedRevision", published.path("revision").asLong()));
        assertEquals(published.path("definitionVersion"), edited.path("definitionVersion"));
        assertTrue(edited.path("publishedDebug").asBoolean());
        assertTrue(edited.path("hasUnpublishedChanges").asBoolean());
        assertEquals(edited, fixture.api.call("POST", path + "/publish", request));

        Map<String, Object> conflictingRequest = new LinkedHashMap<>(request);
        conflictingRequest.put("expectedRevision", edited.path("revision").asLong());
        fixture.api.expectError("POST", path + "/publish", conflictingRequest, 409,
                DefinitionErrorCode.PUBLICATION_REQUEST_CONFLICT.getCode());
        Map<String, Object> staleRequest = new LinkedHashMap<>(request);
        staleRequest.put("requestId", UUID.randomUUID().toString());
        fixture.api.expectError("POST", path + "/publish", staleRequest, 409,
                DefinitionErrorCode.REVISION_CONFLICT.getCode());
        fixture.api.expectError("PUT", path,
                Map.of("name", "stale-editor", "expectedRevision", published.path("revision").asLong()),
                409, DefinitionErrorCode.REVISION_CONFLICT.getCode());
        assertEquals(edited, fixture.api.call("GET", path));
        assertEquals(previousPublications + 1, fixture.api.call("GET", path + "/publications")
                .path("pagination").path("total").asLong());
        assertEquals(1, fixture.api.call("GET", path + "/versions")
                .path("pagination").path("total").asInt());

        var diagnosticsPublication = fixture.api.publish(definition);
        assertEquals(published.path("definitionVersion"), diagnosticsPublication.path("definitionVersion"));
        assertFalse(diagnosticsPublication.path("publishedDebug").asBoolean());
        assertFalse(diagnosticsPublication.path("hasUnpublishedChanges").asBoolean());
        String instance = fixture.submit(definition);
        var execution = fixture.awaitState(instance, "FINISHED");
        assertEquals(published.path("definitionVersion"), execution.path("definitionVersion"));
        assertFalse(execution.path("debug").asBoolean());
    }
}
