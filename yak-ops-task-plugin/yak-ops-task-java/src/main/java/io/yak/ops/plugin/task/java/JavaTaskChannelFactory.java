package io.yak.ops.plugin.task.java;

import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskChannelFactory;

import com.google.auto.service.AutoService;

@AutoService(TaskChannelFactory.class)
public class JavaTaskChannelFactory implements TaskChannelFactory {

    /**
     * Construct a channel for a Java task
     *
     * @return TaskChannel
     **/
    @Override
    public TaskChannel create() {
        return new JavaTaskChannel();
    }

    /**
     * Get a unique identifier of the Java task
     *
     * @return String
     **/
    @Override
    public String getName() {
        return "JAVA";
    }

}
