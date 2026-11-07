package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.stream.StreamTaskChannel;

public class DataSyncTaskChannel implements StreamTaskChannel {

    @Override
    public DataSyncTask createTask(TaskExecutionContext context) {
        return new DataSyncTask(context);
    }

    @Override
    public AbstractParameters parseParameters(String taskParams) {
        return JSONUtils.parseObject(taskParams, DataSyncParameters.class);
    }
}
