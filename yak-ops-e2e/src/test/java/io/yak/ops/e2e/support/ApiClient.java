package io.yak.ops.e2e.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Real HTTP calls with a separate cookie jar and Workspace for every test. */
public final class ApiClient {
    public static final ObjectMapper JSON = new ObjectMapper();
    private final String base;
    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private String workspace;

    public ApiClient(String base) {
        this.base = base;
    }

    public void workspace(String id) {
        workspace = id;
    }

    public String workspace() {
        return workspace;
    }

    public JsonNode call(String method, String path) {
        return call(method, path, null);
    }

    public JsonNode call(String method, String path, Object body) {
        return send(method, path, body, 200, null, 200);
    }

    public JsonNode expect(String method, String path, Object body, int status) {
        return send(method, path, body, status, null, status < 300 ? 200 : null);
    }

    public JsonNode expectError(String method, String path, Object body, int status, int businessCode) {
        return send(method, path, body, status, null, businessCode);
    }

    /** Captures one saved revision so publication retries can reuse the exact same request. */
    public Map<String, Object> publicationRequest(String definition) {
        JsonNode saved = call("GET", "/api/v1/task-definitions/" + definition);
        return Map.of(
                "version", saved.path("definitionVersion").asInt(),
                "expectedRevision", saved.path("revision").asLong(),
                "requestId", UUID.randomUUID().toString());
    }

    public JsonNode publish(String definition) {
        return call("POST", "/api/v1/task-definitions/" + definition + "/publish", publicationRequest(definition));
    }

    public JsonNode submit(String definition) {
        return submit(definition, UUID.randomUUID().toString());
    }

    public JsonNode submit(String definition, String key) {
        return send("POST", "/api/v1/task-definitions/" + definition + "/instances", null, 202, key, 200);
    }

    private JsonNode send(String method, String path, Object body, int status, String key, Integer businessCode) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            if (workspace != null) request.header("X-Workspace-Id", workspace);
            if (key != null) request.header("Idempotency-Key", key);
            request.method(
                    method,
                    body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(json(body)));
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode result = response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body());
            assertEquals(
                    status,
                    response.statusCode(),
                    method + " " + path + "; business code="
                            + result.path("code").asInt(-1));
            if (businessCode != null) {
                assertEquals(businessCode.intValue(), result.path("code").asInt(-1), method + " " + path);
            }
            return result.path("data");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request interrupted", interrupted);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("HTTP request failed: " + method + " " + path, failure);
        }
    }

    public String download(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(30))
                .header("X-Workspace-Id", workspace)
                .GET()
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return response.body();
    }

    public static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("Invalid test JSON", failure);
        }
    }
}
