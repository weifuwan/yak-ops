package io.yak.ops.server.master.integration.fake;

import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskChannelFactory;

import com.google.auto.service.AutoService;
import com.google.common.annotations.VisibleForTesting;

@VisibleForTesting
@AutoService(TaskChannelFactory.class)
public class LogicFakeTaskChannelFactory implements TaskChannelFactory {

    public static final String NAME = "LogicFakeTask";

    @Override
    public TaskChannel create() {
        return new LogicFakeTaskChannel();
    }

    @Override
    public String getName() {
        return NAME;
    }
}
