package io.yak.ops.server.master.utils;

import io.yak.ops.server.master.exception.TaskExecutionContextCreateException;

import org.springframework.dao.DataAccessResourceFailureException;

public class ExceptionUtils {

    public static boolean isDatabaseConnectedFailedException(Throwable e) {
        return e instanceof DataAccessResourceFailureException;
    }

    public static boolean isTaskExecutionContextCreateException(Throwable e) {
        return e instanceof TaskExecutionContextCreateException;
    }

}
