package io.yak.ops.plugin.task.api;

import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

public interface TaskChannel {

    AbstractTask createTask(TaskExecutionContext taskRequest);

    AbstractParameters parseParameters(String taskParams);

}
