package io.yak.ops.plugin.task.python;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

public class PythonTaskChannel implements TaskChannel {

    @Override
    public PythonTask createTask(TaskExecutionContext taskRequest) {
        return new PythonTask(taskRequest);
    }

    @Override
    public AbstractParameters parseParameters(String taskParams) {
        return JSONUtils.parseObject(taskParams, PythonParameters.class);
    }

}
