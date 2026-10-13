package io.yak.ops.boot.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import io.yak.ops.plugin.task.api.log.TaskLogMarkers;
import java.util.List;
import org.slf4j.Marker;

/** Filters events without an authorized Task log identity or explicitly excluded markers. */
public final class TaskLogFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String id = event.getMDCPropertyMap().get("taskLogId");
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) {
            return FilterReply.DENY;
        }
        List<Marker> markers = event.getMarkerList();
        if (markers != null) {
            for (Marker marker : markers) {
                if (marker.contains(TaskLogMarkers.excludeInTaskLog())) {
                    return FilterReply.DENY;
                }
            }
        }
        return FilterReply.NEUTRAL;
    }
}
