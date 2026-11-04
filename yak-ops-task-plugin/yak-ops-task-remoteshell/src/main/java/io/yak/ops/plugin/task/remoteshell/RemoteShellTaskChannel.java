package io.yak.ops.plugin.task.remoteshell;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

public class RemoteShellTaskChannel implements TaskChannel {

    @Override
    public RemoteShellTask createTask(TaskExecutionContext taskRequest) {
        return new RemoteShellTask(taskRequest);
    }

    @Override
    public AbstractParameters parseParameters(String taskParams) {
        return JSONUtils.parseObject(taskParams, RemoteShellParameters.class);
    }

}
