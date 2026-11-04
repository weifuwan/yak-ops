package io.yak.ops.plugin.task.api.task;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.parameters.SwitchParameters;

public class SwitchLogicTaskChannel extends AbstractLogicTaskChannel {

    @Override
    public AbstractParameters parseParameters(String taskParams) {
        return JSONUtils.parseObject(taskParams, SwitchParameters.class);
    }
}
