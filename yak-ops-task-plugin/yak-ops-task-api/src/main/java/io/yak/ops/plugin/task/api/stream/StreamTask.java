package io.yak.ops.plugin.task.api.stream;

public interface StreamTask {

    /** Trigger a non-terminal savepoint and return its durable path. */
    String savePoint() throws Exception;

    /** Stop the stream job gracefully after creating a savepoint and return its path. */
    String stopWithSavepoint() throws Exception;
}
