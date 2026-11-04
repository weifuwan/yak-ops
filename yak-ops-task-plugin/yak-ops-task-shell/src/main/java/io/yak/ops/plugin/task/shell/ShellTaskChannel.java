package io.yak.ops.plugin.task.shell;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

public class ShellTaskChannel implements TaskChannel {

    @Override
    public ShellTask createTask(TaskExecutionContext taskRequest) {
        return new ShellTask(taskRequest);
    }

    @Override
    public AbstractParameters parseParameters(String taskParams) {
        return JSONUtils.parseObject(taskParams, ShellParameters.class);
    }

}
