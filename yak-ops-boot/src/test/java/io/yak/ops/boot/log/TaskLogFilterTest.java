package io.yak.ops.boot.log;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import io.yak.ops.plugin.task.api.log.TaskLogMarkers;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks per-Instance log ownership and explicit marker exclusions. */
class TaskLogFilterTest {

    private final TaskLogFilter filter = new TaskLogFilter();

    @Test
    void excludesEventsWithoutSafeTaskIdentity() {
        LoggingEvent missing = new LoggingEvent();
        missing.setMDCPropertyMap(Map.of());
        assertEquals(FilterReply.DENY, filter.decide(missing));

        LoggingEvent unsafe = new LoggingEvent();
        unsafe.setMDCPropertyMap(Map.of("taskLogId", "../secrets"));
        assertEquals(FilterReply.DENY, filter.decide(unsafe));
    }

    @Test
    void debugEventsRequireInstanceDebugFlag() {
        LoggingEvent suppressed = new LoggingEvent();
        suppressed.setLevel(Level.DEBUG);
        suppressed.setMDCPropertyMap(Map.of("taskLogId", "task-a", "taskLogDebug", "false"));
        assertEquals(FilterReply.DENY, filter.decide(suppressed));

        LoggingEvent accepted = new LoggingEvent();
        accepted.setLevel(Level.DEBUG);
        accepted.setMDCPropertyMap(Map.of("taskLogId", "task-b", "taskLogDebug", "true"));
        assertEquals(FilterReply.NEUTRAL, filter.decide(accepted));

        LoggingEvent info = new LoggingEvent();
        info.setLevel(Level.INFO);
        info.setMDCPropertyMap(Map.of("taskLogId", "task-a", "taskLogDebug", "false"));
        assertEquals(FilterReply.NEUTRAL, filter.decide(info));
    }

    @Test
    void isolatesOrdinaryTaskEventsAndHonorsExclusionMarker() {
        LoggingEvent allowed = new LoggingEvent();
        allowed.setMDCPropertyMap(Map.of("taskLogId", "task-123"));
        assertEquals(FilterReply.NEUTRAL, filter.decide(allowed));

        LoggingEvent excluded = new LoggingEvent();
        excluded.setMDCPropertyMap(Map.of("taskLogId", "task-123"));
        excluded.addMarker(TaskLogMarkers.excludeInTaskLog());
        excluded.addMarker(TaskLogMarkers.includeInTaskLog());
        assertEquals(FilterReply.DENY, filter.decide(excluded));
    }
}
