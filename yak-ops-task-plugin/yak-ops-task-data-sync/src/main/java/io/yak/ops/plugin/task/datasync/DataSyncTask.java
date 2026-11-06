package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.AbstractTask;
import io.yak.ops.plugin.task.api.TaskCallBack;
import io.yak.ops.plugin.task.api.TaskConstants;
import io.yak.ops.plugin.task.api.TaskException;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.stream.StreamTask;

import org.apache.flink.cdc.cli.parser.YamlPipelineDefinitionParser;
import org.apache.flink.cdc.common.configuration.Configuration;
import org.apache.flink.cdc.composer.PipelineExecution;
import org.apache.flink.cdc.composer.definition.PipelineDef;
import org.apache.flink.cdc.composer.flink.FlinkPipelineComposer;
import org.apache.flink.cdc.composer.flink.FlinkPipelineExecution;
import org.apache.flink.core.execution.JobClient;

import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataSyncTask extends AbstractTask implements StreamTask {

    private DataSyncParameters parameters;
    private volatile JobClient jobClient;
    private volatile boolean stopped;

    public DataSyncTask(TaskExecutionContext context) {
        super(context);
    }

    @Override
    public void init() {
        parameters = JSONUtils.parseObject(taskRequest.getTaskParams(), DataSyncParameters.class);
        if (parameters == null || !parameters.checkParameters()) {
            throw new TaskException("DATA_SYNC requires one MySQL table and INITIAL CDC startup");
        }
    }

    @Override
    public void handle(TaskCallBack callback) throws TaskException {
        if (stopped) {
            setExitStatusCode(TaskConstants.EXIT_CODE_KILL);
            return;
        }
        try {
            String pipelineJson = DataSyncPipeline.build(taskRequest, parameters);
            PipelineDef definition = new YamlPipelineDefinitionParser().parse(pipelineJson, new Configuration());
            org.apache.flink.configuration.Configuration flinkConfig = new org.apache.flink.configuration.Configuration();
            flinkConfig.setString("rest.bind-address", "127.0.0.1");
            FlinkPipelineComposer composer = FlinkPipelineComposer.ofMiniCluster(flinkConfig, Collections.emptyList());
            FlinkPipelineExecution execution = (FlinkPipelineExecution) composer.compose(definition);
            PipelineExecution.ExecutionInfo info = execution.executeManaged(client -> {
                jobClient = client;
                log.info("DATA_SYNC Flink job submitted, taskInstanceId={}, jobId={}",
                        taskRequest.getTaskInstanceId(), client.getJobID());
                if (stopped) {
                    client.cancel();
                }
            });
            setExitStatusCode(stopped ? TaskConstants.EXIT_CODE_KILL : TaskConstants.EXIT_CODE_SUCCESS);
            log.info("DATA_SYNC Flink job completed, jobId={}", info.getId());
        } catch (Exception e) {
            if (stopped) {
                setExitStatusCode(TaskConstants.EXIT_CODE_KILL);
                log.info("DATA_SYNC job cancelled, taskInstanceId={}", taskRequest.getTaskInstanceId());
                return;
            }
            setExitStatusCode(TaskConstants.EXIT_CODE_FAILURE);
            throw new TaskException("DATA_SYNC job failed", e);
        } finally {
            jobClient = null;
        }
    }

    @Override
    public void cancel() throws TaskException {
        stopped = true;
        setExitStatusCode(TaskConstants.EXIT_CODE_KILL);
        JobClient client = jobClient;
        if (client != null) {
            try {
                client.cancel().get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TaskException("Interrupted while cancelling CDC job", e);
            } catch (ExecutionException e) {
                throw new TaskException("Could not cancel CDC job", e);
            }
        }
    }

    @Override
    public void savePoint() {
        throw new UnsupportedOperationException("CDC savepoints will be added after the first execution milestone");
    }

    @Override
    public AbstractParameters getParameters() {
        return parameters;
    }
}
