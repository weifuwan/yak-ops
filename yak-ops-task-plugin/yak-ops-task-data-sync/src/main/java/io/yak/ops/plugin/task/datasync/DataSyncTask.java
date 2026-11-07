package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.AbstractTask;
import io.yak.ops.plugin.task.api.TaskCallBack;
import io.yak.ops.plugin.task.api.TaskConstants;
import io.yak.ops.plugin.task.api.TaskException;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.stream.StreamTask;

import java.util.concurrent.ExecutionException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataSyncTask extends AbstractTask implements StreamTask {

    private DataSyncParameters parameters;
    private final DataSyncJobRunner jobRunner;

    public DataSyncTask(TaskExecutionContext context) {
        super(context);
        jobRunner = new DataSyncJobRunner(context);
    }

    @Override
    public void init() {
        parameters = JSONUtils.parseObject(taskRequest.getTaskParams(), DataSyncParameters.class);
        if (parameters == null || !parameters.checkParameters()) {
            throw new TaskException("Invalid DATA_SYNC task configuration");
        }
    }

    @Override
    public void handle(TaskCallBack callback) throws TaskException {
        try {
            jobRunner.execute(parameters);
            setExitStatusCode(jobRunner.isCancellationConfirmed()
                    ? TaskConstants.EXIT_CODE_KILL
                    : TaskConstants.EXIT_CODE_SUCCESS);
        } catch (Exception e) {
            if (jobRunner.isCancellationConfirmed()) {
                setExitStatusCode(TaskConstants.EXIT_CODE_KILL);
                log.info("DATA_SYNC cancelled, taskInstanceId={}", taskRequest.getTaskInstanceId());
            } else {
                setExitStatusCode(TaskConstants.EXIT_CODE_FAILURE);
                throw new TaskException("DATA_SYNC execution failed", e);
            }
        }
    }

    @Override
    public void cancel() throws TaskException {
        try {
            jobRunner.cancel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskException("Interrupted while cancelling the CDC job", e);
        } catch (ExecutionException e) {
            throw new TaskException("Could not cancel the CDC job", e);
        }
    }

    @Override
    public void savePoint() {
        throw new UnsupportedOperationException("DATA_SYNC savepoints are not yet supported");
    }

    @Override
    public AbstractParameters getParameters() {
        return parameters;
    }
}
