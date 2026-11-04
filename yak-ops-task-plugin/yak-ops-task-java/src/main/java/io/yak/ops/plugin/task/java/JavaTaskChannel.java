package io.yak.ops.plugin.task.java;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

public class JavaTaskChannel implements TaskChannel {

    @Override
    public JavaTask createTask(TaskExecutionContext taskRequest) {
        return new JavaTask(taskRequest);
    }

    @Override
    public AbstractParameters parseParameters(String taskParams) {
        return JSONUtils.parseObject(taskParams, JavaParameters.class);
    }

}
