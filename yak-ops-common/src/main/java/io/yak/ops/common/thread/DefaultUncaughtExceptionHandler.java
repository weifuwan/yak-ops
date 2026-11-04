package io.yak.ops.common.thread;

import java.util.concurrent.atomic.LongAdder;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class DefaultUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final DefaultUncaughtExceptionHandler INSTANCE = new DefaultUncaughtExceptionHandler();

    private static final LongAdder uncaughtExceptionCount = new LongAdder();

    public static DefaultUncaughtExceptionHandler getInstance() {
        return INSTANCE;
    }

    public static long getUncaughtExceptionCount() {
        return uncaughtExceptionCount.longValue();
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        uncaughtExceptionCount.add(1);
        log.error("Caught an exception in {}.", t, e);
    }
}
