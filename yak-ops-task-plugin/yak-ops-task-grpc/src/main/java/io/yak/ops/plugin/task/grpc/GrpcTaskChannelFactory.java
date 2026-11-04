package io.yak.ops.plugin.task.grpc;

import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskChannelFactory;

import com.google.auto.service.AutoService;

@AutoService(TaskChannelFactory.class)
public class GrpcTaskChannelFactory implements TaskChannelFactory {

    @Override
    public String getName() {
        return "GRPC";
    }

    @Override
    public TaskChannel create() {
        return new GrpcTaskChannel();
    }
}
