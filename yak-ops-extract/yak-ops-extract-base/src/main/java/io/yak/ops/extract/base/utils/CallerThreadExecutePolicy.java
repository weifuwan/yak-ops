package io.yak.ops.extract.base.utils;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * caller thread execute
 */
@Slf4j
public class CallerThreadExecutePolicy implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        log.warn("queue is full, trigger caller thread execute");
        r.run();
    }
}
