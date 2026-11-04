package io.yak.ops.plugin.task.api.task;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.parameters.SubWorkflowParameters;

public class SubWorkflowLogicTaskChannel extends AbstractLogicTaskChannel {

    @Override
    public AbstractParameters parseParameters(String taskParams) {
        return JSONUtils.parseObject(taskParams, SubWorkflowParameters.class);
    }
}
