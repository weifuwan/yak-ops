package io.yak.ops.server.master.utils;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import com.google.common.truth.Truth;

class MasterThreadFactoryTest {

    @Test
    void getDefaultSchedulerThreadExecutor() {
        ScheduledExecutorService defaultSchedulerThreadExecutor =
                MasterThreadFactory.getDefaultSchedulerThreadExecutor();
        Truth.assertThat(defaultSchedulerThreadExecutor).isNotNull();

        AtomicBoolean taskOneFlag = new AtomicBoolean(false);
        defaultSchedulerThreadExecutor.scheduleWithFixedDelay(() -> {
            taskOneFlag.set(true);
        }, 0,
                1,
                java.util.concurrent.TimeUnit.SECONDS);

        AtomicBoolean taskTwoFlag = new AtomicBoolean(false);
        defaultSchedulerThreadExecutor.scheduleWithFixedDelay(() -> {
            taskTwoFlag.set(true);
        }, 0,
                1,
                java.util.concurrent.TimeUnit.SECONDS);

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Truth.assertThat(taskOneFlag.get()).isTrue();
                    Truth.assertThat(taskTwoFlag.get()).isTrue();
                });

    }
}
