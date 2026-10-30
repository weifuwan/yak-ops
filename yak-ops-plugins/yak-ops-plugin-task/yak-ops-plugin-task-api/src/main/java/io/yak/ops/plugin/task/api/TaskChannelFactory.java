package io.yak.ops.plugin.task.api;

/** Factory for one DolphinScheduler-style TaskChannel, identified by its stable task type. */
public interface TaskChannelFactory {
    String getName();

    TaskChannel create();
}
