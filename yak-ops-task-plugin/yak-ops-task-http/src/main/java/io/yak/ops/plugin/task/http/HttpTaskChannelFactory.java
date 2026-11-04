package io.yak.ops.plugin.task.http;

import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskChannelFactory;

import com.google.auto.service.AutoService;

@AutoService(TaskChannelFactory.class)
public class HttpTaskChannelFactory implements TaskChannelFactory {

    @Override
    public String getName() {
        return "HTTP";
    }

    @Override
    public TaskChannel create() {
        return new HttpTaskChannel();
    }
}
