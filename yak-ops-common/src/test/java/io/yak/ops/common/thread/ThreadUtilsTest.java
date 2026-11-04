package io.yak.ops.common.thread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.Test;

class ThreadUtilsTest {

    @Test
    void newDaemonFixedThreadExecutor() throws InterruptedException {
        ThreadPoolExecutor threadPoolExecutor = ThreadUtils.newDaemonFixedThreadExecutor("DemonThread", 1);
        threadPoolExecutor.execute(() -> {
            throw new IllegalArgumentException("I am an exception");
        });
        Thread.sleep(1_000);
        assertEquals(1, DefaultUncaughtExceptionHandler.getUncaughtExceptionCount());

    }

    @Test
    void newDaemonThreadFactory() {
        final ThreadFactory threadFactory = ThreadUtils.newDaemonThreadFactory("DemonThread-%d");
        final Thread thread = threadFactory.newThread(() -> {
        });
        assertTrue(thread.isDaemon());
        assertEquals("DemonThread-0", thread.getName());
    }
}
