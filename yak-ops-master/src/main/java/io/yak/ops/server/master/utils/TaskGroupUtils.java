package io.yak.ops.server.master.utils;

import io.yak.ops.dao.entity.TaskInstance;

public class TaskGroupUtils {

    /**
     * Check if the task instance is using task group
     */
    public static boolean isUsingTaskGroup(final TaskInstance taskInstance) {
        return taskInstance.getTaskGroupId() > 0;
    }

}
