package io.yak.ops.plugin.task.remoteshell;

import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskChannelFactory;

import com.google.auto.service.AutoService;

@AutoService(TaskChannelFactory.class)
public class RemoteShellTaskChannelFactory implements TaskChannelFactory {

    @Override
    public TaskChannel create() {
        return new RemoteShellTaskChannel();
    }

    @Override
    public String getName() {
        return "REMOTESHELL";
    }

}
