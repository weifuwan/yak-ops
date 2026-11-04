package io.yak.ops.plugin.task.api.log;

import io.yak.ops.plugin.task.api.utils.LogUtils;

import org.slf4j.MDC;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.AbstractDiscriminator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class TaskLogDiscriminator extends AbstractDiscriminator<ILoggingEvent> {

    private String key;

    private String logBase;

    @Override
    public String getDiscriminatingValue(ILoggingEvent event) {
        String taskInstanceLogPath = MDC.get(LogUtils.TASK_INSTANCE_LOG_FULL_PATH_MDC_KEY);
        if (taskInstanceLogPath == null) {
            log.error("The task instance log path is null, please check the logback configuration, log: {}", event);
        }
        return taskInstanceLogPath;
    }
}
