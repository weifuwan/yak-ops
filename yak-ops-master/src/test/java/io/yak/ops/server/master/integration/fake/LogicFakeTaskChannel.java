package io.yak.ops.server.master.integration.fake;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.task.AbstractLogicTaskChannel;

import com.google.common.annotations.VisibleForTesting;

@VisibleForTesting
public class LogicFakeTaskChannel extends AbstractLogicTaskChannel {

    @Override
    public AbstractParameters parseParameters(String taskParams) {
        return JSONUtils.parseObject(taskParams, LogicFakeTaskParameters.class);
    }
}
