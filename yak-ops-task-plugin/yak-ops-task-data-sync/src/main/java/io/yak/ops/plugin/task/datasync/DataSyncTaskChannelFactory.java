package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskChannelFactory;

import com.google.auto.service.AutoService;

@AutoService(TaskChannelFactory.class)
public class DataSyncTaskChannelFactory implements TaskChannelFactory {

    @Override
    public String getName() {
        return "DATA_SYNC";
    }

    @Override
    public TaskChannel create() {
        return new DataSyncTaskChannel();
    }
}
