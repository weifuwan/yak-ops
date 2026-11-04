package io.yak.ops.plugin.task.shell;

import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskChannelFactory;

import com.google.auto.service.AutoService;

@AutoService(TaskChannelFactory.class)
public class ShellTaskChannelFactory implements TaskChannelFactory {

    public static final String NAME = "SHELL";

    @Override
    public TaskChannel create() {
        return new ShellTaskChannel();
    }

    @Override
    public String getName() {
        return NAME;
    }

}
