package io.yak.ops.plugin.task.api.log;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/** Explicit inclusion and exclusion of SLF4J events in a Task Instance log. */
public final class TaskLogMarkers {
    private static final Marker INCLUDE = MarkerFactory.getMarker("TASK_LOG_INCLUDE");
    private static final Marker EXCLUDE = MarkerFactory.getMarker("TASK_LOG_EXCLUDE");

    private TaskLogMarkers() {}

    /** @return task-log inclusion marker when an execution identity is present */
    public static Marker includeInTaskLog() {
        return INCLUDE;
    }

    /** @return marker excluding internal diagnostics from task files */
    public static Marker excludeInTaskLog() {
        return EXCLUDE;
    }
}
