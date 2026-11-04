package io.yak.ops.plugin.task.procedure;

import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskChannelFactory;

import com.google.auto.service.AutoService;

@AutoService(TaskChannelFactory.class)
public class ProcedureTaskChannelFactory implements TaskChannelFactory {

    @Override
    public String getName() {
        return "PROCEDURE";
    }

    @Override
    public TaskChannel create() {
        return new ProcedureTaskChannel();
    }
}
