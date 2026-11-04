package io.yak.ops.plugin.task.api.log;

import io.yak.ops.plugin.task.api.utils.LogUtils;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.MDC;
import org.slf4j.Marker;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is used to filter the log of the task instance.
 */
@Slf4j
public class TaskLogFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String taskInstanceLogPath = MDC.get(LogUtils.TASK_INSTANCE_LOG_FULL_PATH_MDC_KEY);
        // If the taskInstanceLogPath is empty, it means that the log is not related to a task instance.
        if (StringUtils.isEmpty(taskInstanceLogPath)) {
            return FilterReply.DENY;
        }

        // todo: Only when the master is includeInTaskLog, the log should be included in the task instance log, we can
        // remove `excludeInTaskLog` after change the log in plugin
        final Marker marker = event.getMarker();
        if (marker == null) {
            return FilterReply.ACCEPT;
        }
        if (marker.contains(TaskLogMarkers.includeInTaskLog())) {
            return FilterReply.ACCEPT;
        }
        if (marker.contains(TaskLogMarkers.excludeInTaskLog())) {
            return FilterReply.DENY;
        }
        return FilterReply.ACCEPT;
    }
}
