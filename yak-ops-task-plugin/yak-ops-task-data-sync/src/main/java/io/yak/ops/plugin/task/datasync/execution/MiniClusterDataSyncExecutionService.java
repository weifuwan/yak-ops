package io.yak.ops.plugin.task.datasync.execution;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.JobStatus;
import org.apache.flink.cdc.composer.flink.FlinkPipelineExecution;
import org.apache.flink.client.program.MiniClusterClient;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.MetricOptions;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.core.execution.SavepointFormatType;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobVertex;
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.runtime.minicluster.MiniClusterConfiguration;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** MiniCluster-backed execution service used by the current embedded deployment. */
public class MiniClusterDataSyncExecutionService extends AbstractDataSyncExecutionService {

    private static final long OPERATION_TIMEOUT_SECONDS = 60L;

    private final Map<String, RuntimeExecution> executions = new ConcurrentHashMap<>();

    private final FlinkRestDataSyncRecoveryPointService recoveryPointService;

    public MiniClusterDataSyncExecutionService() {
        this(new FlinkRestDataSyncRecoveryPointService());
    }

    MiniClusterDataSyncExecutionService(FlinkRestDataSyncRecoveryPointService recoveryPointService) {
        this.recoveryPointService = recoveryPointService;
    }

    @Override
    protected DataSyncExecutionHandle doSubmit(
                                               FlinkPipelineExecution execution, String executionId,
                                               int taskInstanceId, String restorePath) throws Exception {
        JobGraph jobGraph = execution.getJobGraph();
        if (restorePath != null && !restorePath.trim().isEmpty()) {
            jobGraph.setSavepointRestoreSettings(SavepointRestoreSettings.forPath(restorePath, false));
        }
        int parallelism = Math.max(1, execution.getParallelism());
        for (JobVertex vertex : jobGraph.getVertices()) {
            parallelism = Math.max(parallelism, vertex.getParallelism());
        }
        Configuration configuration = new Configuration();
        configuration.set(RestOptions.ADDRESS, "127.0.0.1");
        configuration.set(RestOptions.BIND_ADDRESS, "127.0.0.1");
        configuration.set(MetricOptions.METRIC_FETCHER_UPDATE_INTERVAL, Duration.ofSeconds(1));

        MiniClusterConfiguration miniClusterConfiguration =
                new MiniClusterConfiguration.Builder()
                        .setConfiguration(configuration)
                        .setNumTaskManagers(1)
                        .setNumSlotsPerTaskManager(parallelism)
                        .withRandomPorts()
                        .build();

        MiniCluster miniCluster = new MiniCluster(miniClusterConfiguration);
        MiniClusterClient client = null;
        try {
            miniCluster.start();
            client = new MiniClusterClient(configuration, miniCluster);
            JobID jobId = client.submitJob(jobGraph)
                    .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String restEndpoint = client.getWebInterfaceURL();
            executions.put(executionId, new RuntimeExecution(miniCluster, client, jobId));
            return DataSyncExecutionHandle.builder()
                    .executionId(executionId)
                    .taskInstanceId(taskInstanceId)
                    .jobId(jobId.toString())
                    .deploymentMode("LOCAL_MINICLUSTER")
                    .clusterId(UUID.randomUUID().toString())
                    .restEndpoint(restEndpoint)
                    .submittedAt(System.currentTimeMillis())
                    .build();
        } catch (Exception exception) {
            try {
                if (client != null) {
                    client.close();
                }
            } finally {
                miniCluster.closeAsync().get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            throw exception;
        }
    }

    @Override
    protected DataSyncExecutionState doQueryState(DataSyncExecutionHandle handle) throws Exception {
        RuntimeExecution runtime = requireRuntime(handle);
        JobStatus status = runtime.client.getJobStatus(runtime.jobId)
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return DataSyncExecutionState.from(status);
    }

    @Override
    protected String doTriggerSavepoint(DataSyncExecutionHandle handle) throws Exception {
        RuntimeExecution runtime = requireRuntime(handle);
        return runtime.client.triggerSavepoint(
                runtime.jobId, savepointDirectory(), SavepointFormatType.CANONICAL)
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    protected String doStopWithSavepoint(DataSyncExecutionHandle handle) throws Exception {
        RuntimeExecution runtime = requireRuntime(handle);
        return runtime.client.stopWithSavepoint(
                runtime.jobId, false, savepointDirectory(), SavepointFormatType.CANONICAL)
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private String savepointDirectory() {
        String configured = System.getProperty("yak.ops.data-sync.savepoint-directory");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("YAK_OPS_DATA_SYNC_SAVEPOINT_DIRECTORY");
        }
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        return new File(new File(System.getProperty("user.dir"), "data"),
                "yak-ops/data-sync/savepoints").toURI().toString();
    }

    @Override
    protected DataSyncRecoveryPointSnapshot doGetLatestCompletedCheckpoint(DataSyncExecutionHandle handle)
            throws Exception {
        return recoveryPointService.latestCompletedCheckpoint(handle);
    }

    @Override
    protected void doCancel(DataSyncExecutionHandle handle) throws Exception {
        RuntimeExecution runtime = executions.get(handle.getExecutionId());
        if (runtime != null) {
            runtime.client.cancel(runtime.jobId).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    protected void doRelease(DataSyncExecutionHandle handle) throws Exception {
        RuntimeExecution runtime = executions.remove(handle.getExecutionId());
        if (runtime == null) {
            return;
        }
        try {
            runtime.client.close();
        } finally {
            runtime.miniCluster.closeAsync().get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private RuntimeExecution requireRuntime(DataSyncExecutionHandle handle) {
        RuntimeExecution runtime = executions.get(handle.getExecutionId());
        if (runtime == null) {
            throw new IllegalStateException("MiniCluster runtime not found: " + handle.getExecutionId());
        }
        return runtime;
    }

    private static final class RuntimeExecution {

        private final MiniCluster miniCluster;
        private final MiniClusterClient client;
        private final JobID jobId;

        private RuntimeExecution(MiniCluster miniCluster, MiniClusterClient client, JobID jobId) {
            this.miniCluster = miniCluster;
            this.client = client;
            this.jobId = jobId;
        }
    }
}
