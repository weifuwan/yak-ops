package io.yak.ops.plugin.task.api.task;

import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskChannelFactory;

import com.google.auto.service.AutoService;

@AutoService(TaskChannelFactory.class)
public class SubWorkflowLogicTaskChannelFactory implements TaskChannelFactory {

    public static final String NAME = "SUB_WORKFLOW";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public TaskChannel create() {
        return new SubWorkflowLogicTaskChannel();
    }
}
