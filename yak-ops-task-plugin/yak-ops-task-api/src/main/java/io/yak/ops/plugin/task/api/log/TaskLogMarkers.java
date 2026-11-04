package io.yak.ops.plugin.task.api.log;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class TaskLogMarkers {

    private static final Marker TASK_LOGGER_EXCLUDE_MARKER = MarkerFactory.getMarker("TASK_LOGGER_EXCLUDE");

    private static final Marker TASK_LOGGER_INCLUDE_MARKER = MarkerFactory.getMarker("TASK_LOGGER_INCLUDE");

    /**
     * The marker used to exclude logs from the task instance log file.
     */
    public static Marker excludeInTaskLog() {
        return TASK_LOGGER_EXCLUDE_MARKER;
    }

    public static Marker includeInTaskLog() {
        return TASK_LOGGER_INCLUDE_MARKER;

    }
}
