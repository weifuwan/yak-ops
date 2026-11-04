package io.yak.ops.task.executor.log;

import io.yak.ops.task.executor.ITaskExecutor;

import org.slf4j.MDC;

public class TaskExecutorMDCUtils {

    private static final String TASK_INSTANCE_ID_MDC_KEY = "taskInstanceId";
    private static final String TASK_INSTANCE_LOG_FULL_PATH_MDC_KEY = "taskInstanceLogFullPath";

    public static MDCAutoClosable logWithMDC(final ITaskExecutor taskExecutor) {
        return logWithMDC(taskExecutor.getId(), taskExecutor.getTaskExecutionContext().getLogPath());
    }

    public static MDCAutoClosable logWithMDC(final int taskInstanceId) {
        return logWithMDC(taskInstanceId, null);
    }

    public static MDCAutoClosable logWithMDC(final int taskInstanceId, final String logPath) {

        if (logPath != null) {
            MDC.put(TASK_INSTANCE_LOG_FULL_PATH_MDC_KEY, logPath);
        }

        MDC.put(TASK_INSTANCE_ID_MDC_KEY, String.valueOf(taskInstanceId));

        return () -> {
            MDC.remove(TASK_INSTANCE_LOG_FULL_PATH_MDC_KEY);
            MDC.remove(TASK_INSTANCE_ID_MDC_KEY);
        };
    }

    public interface MDCAutoClosable extends AutoCloseable {

        @Override
        void close();
    }

}
