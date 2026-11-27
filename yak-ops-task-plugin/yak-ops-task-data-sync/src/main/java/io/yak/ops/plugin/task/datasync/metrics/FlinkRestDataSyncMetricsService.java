package io.yak.ops.plugin.task.datasync.metrics;

import io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionHandle;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Flink REST implementation shared by embedded and remote deployment modes. */
public class FlinkRestDataSyncMetricsService extends AbstractDataSyncMetricsService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int MAX_ERROR_BODY_LENGTH = 2_048;

    private static final List<String> METRIC_NAMES = List.of(
            "numSnapshotRecords",
            "numInsertDMLRecords",
            "numUpdateDMLRecords",
            "numDeleteDMLRecords",
            "numDDLRecords",
            "numCommittedRecords",
            "numRecordsInErrors");

    private final HttpClient httpClient;

    public FlinkRestDataSyncMetricsService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    FlinkRestDataSyncMetricsService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    protected Map<String, Long> collectRawMetrics(DataSyncExecutionHandle handle) throws Exception {
        Map<String, Long> result = new LinkedHashMap<>();
        String baseUrl = normalizeBaseUrl(handle.getRestEndpoint());

        // Flink 1.20 exposes the vertex list on the JobDetails endpoint. There is no
        // /jobs/{jobId}/vertices collection endpoint.
        JsonNode jobResponse = getJson(baseUrl + "/jobs/" + handle.getJobId());
        JsonNode vertices = jobResponse.path("vertices");
        if (!vertices.isArray()) {
            throw new IOException(
                    "Flink REST job response does not contain a vertices array for job "
                            + handle.getJobId()
                            + "; response body: "
                            + truncateResponseBody(jobResponse.toString()));
        }

        for (JsonNode vertex : vertices) {
            String vertexId = vertex.path("id").asText();
            int parallelism = Math.max(1, vertex.path("parallelism").asInt(1));
            for (int subtask = 0; subtask < parallelism; subtask++) {
                String metricPath = "/jobs/" + handle.getJobId() + "/vertices/" + vertexId
                        + "/subtasks/" + subtask + "/metrics";
                JsonNode availableMetrics = getJson(baseUrl + metricPath);
                List<String> metricIds = new ArrayList<>();
                if (availableMetrics.isArray()) {
                    for (JsonNode metric : availableMetrics) {
                        String metricId = metric.path("id").asText();
                        if (isSupportedMetric(metricId)) {
                            metricIds.add(metricId);
                        }
                    }
                }
                if (metricIds.isEmpty()) {
                    continue;
                }

                String requestedMetrics = metricIds.stream()
                        .map(value -> URLEncoder.encode(value, StandardCharsets.UTF_8))
                        .collect(Collectors.joining(","));
                JsonNode values = getJson(baseUrl + metricPath + "?get=" + requestedMetrics);
                if (values.isArray()) {
                    for (JsonNode metric : values) {
                        String metricId = metric.path("id").asText();
                        String value = metric.path("value").asText();
                        try {
                            result.put(
                                    vertexId + ".subtask." + subtask + "." + metricId,
                                    Long.parseLong(value));
                        } catch (NumberFormatException ignored) {
                            // Non-numeric metric values are not record counters.
                        }
                    }
                }
            }
        }
        return result;
    }

    private JsonNode getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "Flink REST returned HTTP " + response.statusCode()
                            + " for " + url
                            + "; response body: " + truncateResponseBody(response.body()));
        }
        return OBJECT_MAPPER.readTree(response.body());
    }

    private static String truncateResponseBody(String body) {
        String normalized = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "<empty>";
        }
        if (normalized.length() > MAX_ERROR_BODY_LENGTH) {
            return normalized.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
        }
        return normalized;
    }

    private static boolean isSupportedMetric(String metricId) {
        for (String metricName : METRIC_NAMES) {
            if (metricId.equals(metricName) || metricId.endsWith("." + metricName)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeBaseUrl(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Flink REST endpoint is required");
        }
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }
}
