package io.yak.ops.plugin.database.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.DataSourcePluginException.Operation;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/** Minimal Elasticsearch management client. Vendor SDKs deliberately stay in Link-Up. */
class ElasticsearchHttpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ElasticsearchConnection connection;
    private final Duration timeout;
    private final HttpClient httpClient;

    ElasticsearchHttpClient(ElasticsearchConnection connection, int timeoutSeconds) {
        this.connection = connection;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    JsonNode get(String path, Operation operation) {
        HttpResponse<String> response = send(path, operation);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure(operation, path, response.statusCode(), response.body());
        }
        return parse(operation, path, response.body());
    }

    JsonNode getOrNull(String path, Operation operation) {
        HttpResponse<String> response = send(path, operation);
        if (response.statusCode() == 404) return null;
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure(operation, path, response.statusCode(), response.body());
        }
        return parse(operation, path, response.body());
    }

    String encodePathSegment(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new DataSourcePluginException(Operation.PARAMETER, "Elasticsearch index 不能为空");
        }
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private HttpResponse<String> send(String path, Operation operation) {
        String normalizedPath = path == null || path.isBlank() ? "/" : path;
        if (!normalizedPath.startsWith("/")) normalizedPath = "/" + normalizedPath;
        URI uri;
        try {
            uri = URI.create(connection.primaryHost() + normalizedPath);
        } catch (IllegalArgumentException exception) {
            throw new DataSourcePluginException(operation, "Elasticsearch 请求地址不合法", exception);
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET();
        if (connection.username() != null && !connection.username().isBlank()) {
            String credential =
                    connection.username() + ":" + (connection.password() == null ? "" : connection.password());
            request.header(
                    "Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8)));
        }

        try {
            return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DataSourcePluginException(operation, "Elasticsearch 请求被中断", exception);
        } catch (IOException exception) {
            throw new DataSourcePluginException(operation, "无法连接 Elasticsearch：" + connection.primaryHost(), exception);
        }
    }

    private JsonNode parse(Operation operation, String path, String body) {
        try {
            return body == null || body.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new DataSourcePluginException(operation, "Elasticsearch 返回了无法解析的 JSON：" + path, exception);
        }
    }

    private DataSourcePluginException failure(Operation operation, String path, int statusCode, String body) {
        String message = null;
        if (body != null && !body.isBlank()) {
            try {
                JsonNode error = MAPPER.readTree(body);
                message = error.path("error").path("reason").asText(null);
                if (message == null || message.isBlank()) {
                    message = error.path("error").asText(null);
                }
            } catch (JsonProcessingException ignored) {
                // Keep the status-only fallback and never copy arbitrary HTML/proxy bodies into errors.
            }
        }
        String suffix = message == null || message.isBlank() ? "" : "：" + message;
        return new DataSourcePluginException(operation, "Elasticsearch HTTP " + statusCode + " " + path + suffix);
    }
}
