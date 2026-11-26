package io.yak.ops.plugin.task.datasync.execution;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.JobStatus;
import org.apache.flink.client.program.MiniClusterClient;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.MetricOptions;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.cdc.composer.flink.FlinkPipelineExecution;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.runtime.minicluster.MiniClusterConfiguration;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** MiniCluster-backed execution service used by the current embedded deployment. */
public class MiniClusterDataSyncExecutionService extends AbstractDataSyncExecutionService {

    private static final long OPERATION_TIMEOUT_SECONDS = 60L;

    private final Map<String, RuntimeExecution> executions = new ConcurrentHashMap<>();

    @Override
    protected DataSyncExecutionHandle doSubmit(
                                               FlinkPipelineExecution execution, String executionId,
                                               int taskInstanceId) throws Exception {
        int parallelism = Math.max(1, execution.getParallelism());
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
            JobID jobId = client.submitJob(execution.getJobGraph())
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
