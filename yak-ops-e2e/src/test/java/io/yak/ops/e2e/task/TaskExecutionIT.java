package io.yak.ops.e2e.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.e2e.support.BackendTestBase;
import io.yak.ops.e2e.support.BootProcess;
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
        String instance = fixture.submit(definition);
        fixture.awaitState(instance, "FINISHED");
        var old = fixture.api.call("GET", "/api/v1/task-instances/" + instance + "/definition");
        fixture.api.call("POST", "/api/v1/task-definitions/" + definition + "/unpublish");
        fixture.createTarget("changed_orders");
        var replacement = fixture.parameters(
                "SINGLE_TABLE_OFFLINE", List.of("FULL"), List.of(fixture.route("orders", "changed_orders")));
        var updated = fixture.api.call(
                "PUT",
                "/api/v1/task-definitions/" + definition,
                Map.of(
                        "name", "renamed-task",
                        "debug", false,
                        "parameters", replacement));
        assertTrue(updated.path("definitionVersion").asInt() > old.path("version").asInt());
        fixture.api.call("POST", "/api/v1/task-definitions/" + definition + "/publish");
        String next = fixture.submit(definition);
        fixture.awaitState(next, "FINISHED");
        var retained = fixture.api.call("GET", "/api/v1/task-instances/" + instance + "/definition");
        assertEquals(old, retained);
        assertEquals(
                updated.path("definitionVersion").asInt(),
                fixture.api.call("GET", "/api/v1/task-instances/" + next + "/definition")
                        .path("version").asInt());
        fixture.assertTarget("changed_orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
    }
}
