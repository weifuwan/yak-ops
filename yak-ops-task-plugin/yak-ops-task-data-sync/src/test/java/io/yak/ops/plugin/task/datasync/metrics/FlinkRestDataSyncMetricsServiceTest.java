package io.yak.ops.plugin.task.datasync.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionHandle;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class FlinkRestDataSyncMetricsServiceTest {

    @Test
    void aggregatesCountersAcrossSubtasksInsteadOfOverwritingMetricIds() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            if ("/jobs/job-1".equals(path)) {
                body = "{\"jid\":\"job-1\",\"name\":\"data-sync-test\",\"state\":\"RUNNING\","
                        + "\"vertices\":[{\"id\":\"source\",\"parallelism\":2},"
                        + "{\"id\":\"sink\",\"parallelism\":2}]}";
            } else if (path.endsWith("/jm-operator-metrics")) {
                String[] segments = path.split("/");
                String vertex = segments[4];
                String query = exchange.getRequestURI().getRawQuery();
                if (query == null) {
                    body = coordinatorMetricList(vertex);
                } else {
                    String requested = URLDecoder.decode(
                            query.substring("get=".length()), StandardCharsets.UTF_8.name());
                    body = coordinatorMetricValues(vertex, requested.split(","));
                }
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
            assertEquals(1, snapshot.getTables().size());
            DataSyncTableMetrics table = snapshot.getTables().get(0);
            assertEquals("db.orders", table.getSourceTable());
            assertEquals("db.orders", table.getSinkTable());
            assertEquals(39L, table.getReadRecords());
            assertEquals(20L, table.getCommittedRecords());
            assertEquals("AVAILABLE", table.getMetricsState());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void collectsJobManagerPhaseSchemaAndSnapshotSplitMetrics() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            if ("/jobs/job-1".equals(path)) {
                body = "{\\"jid\\":\\"job-1\\",\\"name\\":\\"data-sync-test\\",\\"state\\":\\"RUNNING\\","
                        + "\\"vertices\\":[{\\"id\\":\\"source\\",\\"parallelism\\":1},"
                        + "{\\"id\\":\\"sink\\",\\"parallelism\\":1}]}";
            } else if (path.endsWith("/jm-operator-metrics")) {
                String[] segments = path.split("/");
                String query = exchange.getRequestURI().getRawQuery();
                if (query == null) {
                    body = coordinatorMetricList(segments[4]);
                } else {
                    String requested = URLDecoder.decode(
                            query.substring("get=".length()), StandardCharsets.UTF_8.name());
                    body = coordinatorMetricValues(segments[4], requested.split(","));
                }
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
                    .executionId("execution-phase")
                    .jobId("job-1")
                    .restEndpoint(endpoint)
                    .build();

            DataSyncExecutionSnapshot snapshot =
                    new FlinkRestDataSyncMetricsService().collect(handle);

            assertEquals(Boolean.TRUE, snapshot.getSnapshotting());
            assertEquals(Boolean.FALSE, snapshot.getStreamReading());
            assertEquals(1, snapshot.getSourceTablesTotal());
            assertEquals(1, snapshot.getSchemaTablesPrepared());
            assertTrue(snapshot.getSchemaPreparedTables().contains("target_db.orders_target"));
            assertEquals(1, snapshot.getTableSplits().size());
            assertEquals("db.orders", snapshot.getTableSplits().get(0).getTableIdentifier());
            assertEquals(10L, snapshot.getTableSplits().get(0).getPlannedSplits());
            assertEquals(2L, snapshot.getTableSplits().get(0).getCompletedSplits());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void keepsCoordinatorPhaseMetricsWhenSubtaskMetricsAreNotReady() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/jobs/job-1".equals(path)) {
                String body = "{\\"jid\\":\\"job-1\\",\\"name\\":\\"data-sync-test\\","
                        + "\\"state\\":\\"RUNNING\\",\\"vertices\\":["
                        + "{\\"id\\":\\"source\\",\\"parallelism\\":1},"
                        + "{\\"id\\":\\"sink\\",\\"parallelism\\":1}]}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (java.io.OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
                return;
            }

            String body;
            int status;
            if (path.endsWith("/jm-operator-metrics")) {
                String[] segments = path.split("/");
                String query = exchange.getRequestURI().getRawQuery();
                if (query == null) {
                    body = coordinatorMetricList(segments[4]);
                } else {
                    String requested = URLDecoder.decode(
                            query.substring("get=".length()), StandardCharsets.UTF_8.name());
                    body = coordinatorMetricValues(segments[4], requested.split(","));
                }
                status = 200;
            } else {
                body = "{\\"errors\\":[\\"Task vertex is still initializing\\"]}";
                status = 500;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            DataSyncExecutionHandle handle = DataSyncExecutionHandle.builder()
                    .executionId("execution-initializing")
                    .jobId("job-1")
                    .restEndpoint(endpoint)
                    .build();

            DataSyncExecutionSnapshot snapshot =
                    new FlinkRestDataSyncMetricsService().collect(handle);

            assertEquals(Boolean.TRUE, snapshot.getSnapshotting());
            assertEquals(1, snapshot.getSchemaTablesPrepared());
            assertTrue(snapshot.getSchemaPreparedTables().contains("target_db.orders_target"));
            assertEquals("PARTIAL", snapshot.getMetricsState());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void includesRestErrorResponseBodyWhenRequestFails() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = "{\"errors\":[\"Job not found in dispatcher\"]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(404, bytes.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            DataSyncExecutionHandle handle = DataSyncExecutionHandle.builder()
                    .executionId("execution-404")
                    .jobId("missing-job")
                    .restEndpoint(endpoint)
                    .build();

            IOException exception = assertThrows(
                    IOException.class,
                    () -> new FlinkRestDataSyncMetricsService().collect(handle));

            assertTrue(exception.getMessage().contains("HTTP 404"));
            assertTrue(exception.getMessage().contains("Job not found in dispatcher"));
        } finally {
            server.stop(0);
        }
    }

    private static String coordinatorMetricList(String vertex) {
        if (!"source".equals(vertex)) {
            return "[]";
        }
        return "[{\\"id\\":\\"isSnapshotting\\"},"
                + "{\\"id\\":\\"isStreamReading\\"},"
                + "{\\"id\\":\\"numTablesSnapshotted\\"},"
                + "{\\"id\\":\\"numTablesRemaining\\"},"
                + "{\\"id\\":\\"numSchemaTablesPrepared\\"},"
                + "{\\"id\\":\\"source.namespace.db.schema..table.orders.numSnapshotSplitsProcessed\\"},"
                + "{\\"id\\":\\"source.namespace.db.schema..table.orders.numSnapshotSplitsRemaining\\"},"
                + "{\\"id\\":\\"source.namespace.db.schema..table.orders.numSnapshotSplitsFinished\\"},"
                + "{\\"id\\":\\"schema.namespace..schema.target_db.table.orders_target.schemaPrepared\\"}]";
    }

    private static String coordinatorMetricValues(String vertex, String[] requested) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < requested.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            String metricId = requested[i];
            long value = 0L;
            if ("source".equals(vertex)) {
                if ("isSnapshotting".equals(metricId) || metricId.endsWith(".isSnapshotting")) {
                    value = 1L;
                } else if ("numTablesRemaining".equals(metricId) || metricId.endsWith(".numTablesRemaining")) {
                    value = 1L;
                } else if ("numSchemaTablesPrepared".equals(metricId) || metricId.endsWith(".numSchemaTablesPrepared")) {
                    value = 1L;
                } else if (metricId.endsWith(".numSnapshotSplitsProcessed")) {
                    value = 4L;
                } else if (metricId.endsWith(".numSnapshotSplitsRemaining")) {
                    value = 6L;
                } else if (metricId.endsWith(".numSnapshotSplitsFinished")) {
                    value = 2L;
                } else if (metricId.endsWith(".schemaPrepared")) {
                    value = 1L;
                }
            }
            json.append("{\\"id\\":\\"").append(metricId)
                    .append("\\",\\"value\\":\\"").append(value).append("\\"}");
        }
        return json.append(']').toString();
    }

    private static String metricList(String vertex) {
        if ("source".equals(vertex)) {
            return "[{\"id\":\"numSnapshotRecords\"},"
                    + "{\"id\":\"numInsertDMLRecords\"},"
                    + "{\"id\":\"numUpdateDMLRecords\"},"
                    + "{\"id\":\"numDeleteDMLRecords\"},"
                    + "{\"id\":\"source.namespace.db.schema..table.orders.numSnapshotRecords\"},"
                    + "{\"id\":\"source.namespace.db.schema..table.orders.numInsertDMLRecords\"},"
                    + "{\"id\":\"source.namespace.db.schema..table.orders.numUpdateDMLRecords\"},"
                    + "{\"id\":\"source.namespace.db.schema..table.orders.numDeleteDMLRecords\"}]";
        }
        return "[{\"id\":\"numCommittedRecords\"},"
                + "{\"id\":\"sink.namespace..schema.db.table.orders.numCommittedRecords\"}]";
    }

    private static String metricValues(String vertex, int subtask, String[] requested) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < requested.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            String metricName = requested[i].substring(requested[i].lastIndexOf('.') + 1);
            long value;
            if ("source".equals(vertex)) {
                value = sourceValue(metricName, subtask);
            } else {
                value = "numCommittedRecords".equals(metricName)
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
