package io.yak.ops.plugin.task.python;

import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskChannelFactory;

import com.google.auto.service.AutoService;

@AutoService(TaskChannelFactory.class)
public class PythonTaskChannelFactory implements TaskChannelFactory {

    @Override
    public TaskChannel create() {
        return new PythonTaskChannel();
    }

    @Override
    public String getName() {
        return "PYTHON";
    }

}
