package io.yak.ops.common.thread;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class ThreadUtils {

    /**
     * Create a daemon fixed thread pool, the thread name will be formatted with the given name.
     *
     * @param threadNameFormat the thread name format, e.g. "DemonThread-%d"
     * @param threadsNum       the number of threads in the pool
     */
    public static ThreadPoolExecutor newDaemonFixedThreadExecutor(String threadNameFormat, int threadsNum) {
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsNum, newDaemonThreadFactory(threadNameFormat));
    }

    public static ScheduledExecutorService newSingleDaemonScheduledExecutorService(String threadNameFormat) {
        return Executors.newSingleThreadScheduledExecutor(newDaemonThreadFactory(threadNameFormat));
    }

    /**
     * Create a daemon scheduler thread pool, the thread name will be formatted with the given name.
     *
     * @param threadNameFormat the thread name format, e.g. "DemonThread-%d"
     * @param threadsNum       the number of threads in the pool
     */
    public static ScheduledExecutorService newDaemonScheduledExecutorService(final String threadNameFormat,
                                                                             final int threadsNum) {
        return Executors.newScheduledThreadPool(threadsNum, newDaemonThreadFactory(threadNameFormat));
    }

    /**
     * Create a daemon thread factory, the thread name will be formatted with the given name.
     *
     * @param threadNameFormat the thread name format, e.g. "DS-DemonThread-%d"
     */
    public static ThreadFactory newDaemonThreadFactory(String threadNameFormat) {
        return new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat(threadNameFormat)
                .setUncaughtExceptionHandler(DefaultUncaughtExceptionHandler.getInstance())
                .build();
    }

    /**
     * Sleep in given mills, this is not accuracy.
     */
    public static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.error("Current thread sleep error", interruptedException);
        }
    }

    public static void rethrowInterruptedException(InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Current thread: " + Thread.currentThread().getName() + " is interrupted",
                interruptedException);
    }

    public static void consumeInterruptedException(InterruptedException interruptedException) {
        log.info("Current thread: {} is interrupted", Thread.currentThread().getName(), interruptedException);
        Thread.currentThread().interrupt();
    }
}
