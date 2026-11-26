package org.apache.flink.cdc.composer.flink;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.JobStatus;
import org.apache.flink.client.program.MiniClusterClient;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.metrics.Counter;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobVertex;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.runtime.minicluster.MiniClusterConfiguration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 proof of concept for owning a MiniCluster and reading job metrics through its REST endpoint.
 *
 * <p>This deliberately starts MiniCluster directly instead of using LocalExecutor. The direct
 * ownership is what makes the REST address and shutdown lifecycle observable without reflection.
 */
class MiniClusterRestApiPocTest {

    private static final int PARALLELISM = 2;
    private static final int RECORDS_PER_SUBTASK = 3;
    private static final long TIMEOUT_SECONDS = 30L;

    @Test
    void shouldReadCustomSourceAndSinkMetricsWhileRunningAndAfterFinished() throws Exception {
        MiniCluster miniCluster = startMiniCluster();
        MiniClusterClient client = new MiniClusterClient(new Configuration(), miniCluster);
        try {
            String restUrl = client.getWebInterfaceURL();
            assertTrue(restUrl.startsWith("http://"), "MiniCluster must expose an HTTP REST URL");

            JobGraph jobGraph = createJobGraph(8_000L, false);
            JobID jobId = client.submitJob(jobGraph).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            JobVertex source = findVertex(jobGraph, "p0-source");
            JobVertex sink = findVertex(jobGraph, "p0-sink");

            awaitMetricSum(restUrl, jobId, source, "numSnapshotRecords", 6L);
            awaitMetricSum(restUrl, jobId, sink, "numCommittedRecords", 6L);
            assertEquals(JobStatus.RUNNING,
                    client.getJobStatus(jobId).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertRestStatus(restUrl, jobId, JobStatus.RUNNING);

            client.requestJobResult(jobId).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertEquals(JobStatus.FINISHED,
                    client.getJobStatus(jobId).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertRestStatus(restUrl, jobId, JobStatus.FINISHED);

            // The cluster is intentionally still owned by this test after job termination.
            // This verifies whether archived terminal jobs still expose the final custom counters.
            assertEquals(6L, sumSubtaskMetric(restUrl, jobId, source, "numSnapshotRecords"));
            assertEquals(6L, sumSubtaskMetric(restUrl, jobId, sink, "numCommittedRecords"));
        } finally {
            miniCluster.closeAsync().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldKeepRestEndpointAvailableAfterCancellation() throws Exception {
        MiniCluster miniCluster = startMiniCluster();
        MiniClusterClient client = new MiniClusterClient(new Configuration(), miniCluster);
        try {
            String restUrl = client.getWebInterfaceURL();
            JobGraph jobGraph = createJobGraph(60_000L, false);
            JobID jobId = client.submitJob(jobGraph).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            JobVertex source = findVertex(jobGraph, "p0-source");
            JobVertex sink = findVertex(jobGraph, "p0-sink");

            awaitMetricSum(restUrl, jobId, source, "numSnapshotRecords", 6L);
            awaitMetricSum(restUrl, jobId, sink, "numCommittedRecords", 6L);

            client.cancel(jobId).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            awaitJobStatus(client, jobId, JobStatus.CANCELED);
            assertRestStatus(restUrl, jobId, JobStatus.CANCELED);

            assertEquals(6L, sumSubtaskMetric(restUrl, jobId, source, "numSnapshotRecords"));
            assertEquals(6L, sumSubtaskMetric(restUrl, jobId, sink, "numCommittedRecords"));
        } finally {
            miniCluster.closeAsync().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldKeepRestEndpointAvailableAfterJobFailure() throws Exception {
        MiniCluster miniCluster = startMiniCluster();
        MiniClusterClient client = new MiniClusterClient(new Configuration(), miniCluster);
        try {
            String restUrl = client.getWebInterfaceURL();
            JobGraph jobGraph = createJobGraph(1_500L, true);
            JobID jobId = client.submitJob(jobGraph).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            JobVertex source = findVertex(jobGraph, "p0-source");
            JobVertex sink = findVertex(jobGraph, "p0-sink");

            awaitMetricSum(restUrl, jobId, source, "numSnapshotRecords", 6L);
            awaitMetricSum(restUrl, jobId, sink, "numCommittedRecords", 6L);
            awaitJobStatus(client, jobId, JobStatus.FAILED);
            assertRestStatus(restUrl, jobId, JobStatus.FAILED);

            assertEquals(6L, sumSubtaskMetric(restUrl, jobId, source, "numSnapshotRecords"));
            assertEquals(6L, sumSubtaskMetric(restUrl, jobId, sink, "numCommittedRecords"));
        } finally {
            miniCluster.closeAsync().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static MiniCluster startMiniCluster() throws Exception {
        Configuration configuration = new Configuration();
        configuration.set(RestOptions.ADDRESS, "127.0.0.1");
        configuration.set(RestOptions.BIND_ADDRESS, "127.0.0.1");

        MiniClusterConfiguration miniClusterConfiguration =
                new MiniClusterConfiguration.Builder()
                        .setConfiguration(configuration)
                        .setNumTaskManagers(1)
                        .setNumSlotsPerTaskManager(PARALLELISM)
                        .withRandomPorts()
                        .build();

        MiniCluster miniCluster = new MiniCluster(miniClusterConfiguration);
        miniCluster.start();
        return miniCluster;
    }

    private static JobGraph createJobGraph(long holdMillis, boolean failAfterHold) {
        StreamExecutionEnvironment environment =
                StreamExecutionEnvironment.createLocalEnvironment(
                        PARALLELISM, new Configuration());
        environment.setParallelism(PARALLELISM);
        environment.disableOperatorChaining();

        environment.addSource(new MetricsSource(holdMillis, failAfterHold))
                .name("p0-source")
                .setParallelism(PARALLELISM)
                .addSink(new MetricsSink())
                .name("p0-sink")
                .setParallelism(PARALLELISM);

        return environment.getStreamGraph().getJobGraph();
    }

    private static JobVertex findVertex(JobGraph jobGraph, String nameFragment) {
        List<JobVertex> vertices = jobGraph.getVerticesSortedTopologically();
        for (JobVertex vertex : vertices) {
            if (vertex.getName().contains(nameFragment)) {
                return vertex;
            }
        }
        throw new AssertionError("Cannot find JobVertex containing name: " + nameFragment);
    }

    private static void awaitMetricSum(
            String restUrl, JobID jobId, JobVertex vertex, String metricName, long expected)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        Exception lastException = null;
        while (System.nanoTime() < deadline) {
            try {
                long actual = sumSubtaskMetric(restUrl, jobId, vertex, metricName);
                if (actual == expected) {
                    return;
                }
            } catch (Exception exception) {
                lastException = exception;
            }
            Thread.sleep(200L);
        }
        throw new AssertionError(
                "Timed out waiting for metric " + metricName + " to reach " + expected,
                lastException);
    }

    private static long sumSubtaskMetric(
            String restUrl, JobID jobId, JobVertex vertex, String metricName) throws Exception {
        long total = 0L;
        for (int subtask = 0; subtask < vertex.getParallelism(); subtask++) {
            String path = "/jobs/" + jobId + "/vertices/" + vertex.getID()
                    + "/subtasks/" + subtask + "/metrics";
            String metricList = httpGet(restUrl, path);
            String metricId = findMetricId(metricList, metricName);
            String metricValues = httpGet(
                    restUrl, path + "?get=" + URLEncoder.encode(metricId, StandardCharsets.UTF_8.name()));
            total += findMetricValue(metricValues);
        }
        return total;
    }

    private static String findMetricId(String response, String metricName) {
        Pattern pattern = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(response);
        while (matcher.find()) {
            String metricId = matcher.group(1);
            if (metricId.endsWith(metricName) || metricId.equals(metricName)) {
                return metricId;
            }
        }
        throw new AssertionError(
                "Metric " + metricName + " was not present in REST metric list: " + response);
    }

    private static long findMetricValue(String response) {
        Matcher matcher = Pattern.compile("\"value\"\\s*:\\s*\"?(\\d+)\"?").matcher(response);
        if (!matcher.find()) {
            throw new AssertionError("Metric value was not present in REST response: " + response);
        }
        return Long.parseLong(matcher.group(1));
    }

    private static void awaitJobStatus(
            MiniClusterClient client, JobID jobId, JobStatus expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        JobStatus actual = null;
        while (System.nanoTime() < deadline) {
            actual = client.getJobStatus(jobId).get(5L, TimeUnit.SECONDS);
            if (actual == expected) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError(
                "Timed out waiting for job " + jobId + " to reach " + expected + ", actual=" + actual);
    }

    private static void assertRestStatus(String restUrl, JobID jobId, JobStatus expected)
            throws Exception {
        String response = httpGet(restUrl, "/jobs/" + jobId + "/status");
        assertTrue(response.contains(expected.name()),
                "Expected REST status " + expected + " but received: " + response);
    }

    private static String httpGet(String baseUrl, String path) throws Exception {
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        HttpURLConnection connection =
                (HttpURLConnection) new URL(normalizedBaseUrl + path).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        try {
            int status = connection.getResponseCode();
            InputStream inputStream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String response = inputStream == null
                    ? ""
                    : new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(200, status, "GET " + path + " returned: " + response);
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private static final class MetricsSource extends RichParallelSourceFunction<Long> {

        private final long holdMillis;
        private final boolean failAfterHold;
        private volatile boolean running = true;

        private MetricsSource(long holdMillis, boolean failAfterHold) {
            this.holdMillis = holdMillis;
            this.failAfterHold = failAfterHold;
        }

        @Override
        public void run(SourceContext<Long> context) throws Exception {
            Counter snapshotRecords =
                    getRuntimeContext().getMetricGroup().counter("numSnapshotRecords");

            for (long record = 0; record < RECORDS_PER_SUBTASK && running; record++) {
                synchronized (context.getCheckpointLock()) {
                    context.collect(record);
                    snapshotRecords.inc();
                }
            }

            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(holdMillis);
            while (running && System.nanoTime() < deadline) {
                Thread.sleep(50L);
            }

            if (running && failAfterHold) {
                throw new IllegalStateException("P0 deliberate source failure");
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    private static final class MetricsSink extends RichSinkFunction<Long> {

        private transient Counter committedRecords;

        @Override
        public void invoke(Long value, Context context) {
            if (committedRecords == null) {
                committedRecords =
                        getRuntimeContext().getMetricGroup().counter("numCommittedRecords");
            }
            committedRecords.inc();
        }
    }
}
