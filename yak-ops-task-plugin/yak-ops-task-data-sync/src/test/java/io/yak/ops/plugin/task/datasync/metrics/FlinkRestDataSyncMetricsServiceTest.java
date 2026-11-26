package io.yak.ops.plugin.task.datasync.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionHandle;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

class FlinkRestDataSyncMetricsServiceTest {

    @Test
    void aggregatesCountersAcrossSubtasksInsteadOfOverwritingMetricIds() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            if ("/jobs/job-1/vertices".equals(path)) {
                body = "{\"vertices\":[{\"id\":\"source\",\"parallelism\":2},"
                        + "{\"id\":\"sink\",\"parallelism\":2}]}";
            } else {
                String[] segments = path.split("/");
                String vertex = segments[4];
                int subtask = Integer.parseInt(segments[6]);
                String query = exchange.getRequestURI().getRawQuery();
                if (query == null) {
                    body = metricList(vertex);
                } else {
                    String requested = URLDecoder.decode(
                            query.substring("get=".length()), StandardCharsets.UTF_8.name());
                    body = metricValues(vertex, subtask, requested.split(","));
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            DataSyncExecutionHandle handle = DataSyncExecutionHandle.builder()
                    .executionId("execution-1")
                    .jobId("job-1")
                    .restEndpoint(endpoint)
                    .build();

            DataSyncExecutionSnapshot snapshot =
                    new FlinkRestDataSyncMetricsService().collect(handle);

            assertEquals(39L, snapshot.getReadRecords());
            assertEquals(20L, snapshot.getCommittedRecords());
            assertEquals("AVAILABLE", snapshot.getMetricsState());
        } finally {
            server.stop(0);
        }
    }

    private static String metricList(String vertex) {
        if ("source".equals(vertex)) {
            return "[{\"id\":\"numSnapshotRecords\"},"
                    + "{\"id\":\"numInsertDMLRecords\"},"
                    + "{\"id\":\"numUpdateDMLRecords\"},"
                    + "{\"id\":\"numDeleteDMLRecords\"}]";
        }
        return "[{\"id\":\"numCommittedRecords\"}]";
    }

    private static String metricValues(String vertex, int subtask, String[] requested) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < requested.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            long value;
            if ("source".equals(vertex)) {
                value = sourceValue(requested[i], subtask);
            } else {
                value = "numCommittedRecords".equals(requested[i])
                        ? (subtask == 0 ? 8L : 12L)
                        : 0L;
            }
            json.append("{\"id\":\"").append(requested[i])
                    .append("\",\"value\":\"").append(value).append("\"}");
        }
        return json.append(']').toString();
    }

    private static long sourceValue(String metric, int subtask) {
        if ("numSnapshotRecords".equals(metric)) {
            return subtask == 0 ? 10L : 20L;
        }
        if ("numInsertDMLRecords".equals(metric)) {
            return subtask == 0 ? 2L : 3L;
        }
        if ("numUpdateDMLRecords".equals(metric)) {
            return subtask == 0 ? 1L : 2L;
        }
        if ("numDeleteDMLRecords".equals(metric)) {
            return subtask == 0 ? 0L : 1L;
        }
        return 0L;
    }
}
