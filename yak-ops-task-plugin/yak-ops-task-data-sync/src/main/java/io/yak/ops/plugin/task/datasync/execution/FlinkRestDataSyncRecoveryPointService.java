package io.yak.ops.plugin.task.datasync.execution;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads the latest completed checkpoint from the public Flink REST API. */
public class FlinkRestDataSyncRecoveryPointService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;

    public FlinkRestDataSyncRecoveryPointService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    FlinkRestDataSyncRecoveryPointService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public DataSyncRecoveryPointSnapshot latestCompletedCheckpoint(DataSyncExecutionHandle handle)
            throws IOException, InterruptedException {
        if (handle == null || handle.getRestEndpoint() == null || handle.getJobId() == null) {
            throw new IllegalArgumentException("Flink REST endpoint and job id are required");
        }
        String baseUrl = handle.getRestEndpoint().endsWith("/")
                ? handle.getRestEndpoint().substring(0, handle.getRestEndpoint().length() - 1)
                : handle.getRestEndpoint();
        String url = baseUrl + "/jobs/" + handle.getJobId() + "/checkpoints";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Flink REST returned HTTP " + response.statusCode()
                    + " for checkpoint query: " + response.body());
        }

        JsonNode completed = OBJECT_MAPPER.readTree(response.body()).path("latest").path("completed");
        if (completed.isMissingNode() || completed.isNull()) {
            return null;
        }
        JsonNode statusNode = completed.get("status");
        String status = statusNode == null || statusNode.isNull() ? "COMPLETED" : statusNode.asText();
        JsonNode checkpointIdNode = completed.get("id");
        String checkpointId = checkpointIdNode == null || checkpointIdNode.isNull()
                ? "" : checkpointIdNode.asText();
        JsonNode restorePathNode = completed.get("external_path");
        String restorePath = restorePathNode == null || restorePathNode.isNull()
                ? "" : restorePathNode.asText();
        long completedAt = completed.path("latest_ack_timestamp")
                .asLong(completed.path("trigger_timestamp").asLong(0L));
        if (!"COMPLETED".equalsIgnoreCase(status)
                || checkpointId.trim().isEmpty()
                || restorePath.trim().isEmpty()
                || completedAt <= 0L) {
            return null;
        }

        String recoveryPointId = handle.getExecutionId() + ":checkpoint:" + checkpointId;
        return DataSyncRecoveryPointSnapshot.builder()
                .recoveryPointId(recoveryPointId)
                .checkpointId(checkpointId)
                .recoveryType("CHECKPOINT")
                .restorePath(restorePath)
                .completedAt(completedAt)
                .build();
    }
}
