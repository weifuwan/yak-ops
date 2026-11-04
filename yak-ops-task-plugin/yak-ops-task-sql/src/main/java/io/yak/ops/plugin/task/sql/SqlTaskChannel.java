package io.yak.ops.plugin.task.sql;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.AbstractTask;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.parameters.SqlParameters;

public class SqlTaskChannel implements TaskChannel {

    @Override
    public AbstractTask createTask(TaskExecutionContext taskRequest) {
        return new SqlTask(taskRequest);
    }

    @Override
    public AbstractParameters parseParameters(String taskParams) {
        return JSONUtils.parseObject(taskParams, SqlParameters.class);
    }

}
