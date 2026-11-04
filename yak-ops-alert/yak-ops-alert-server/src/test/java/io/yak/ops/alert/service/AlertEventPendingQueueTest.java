package io.yak.ops.alert.service;

import static com.google.common.truth.Truth.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import io.yak.ops.alert.config.AlertConfig;
import io.yak.ops.dao.entity.Alert;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lombok.SneakyThrows;

class AlertEventPendingQueueTest {

    private AlertEventPendingQueue alertEventPendingQueue;

    private static final int QUEUE_SIZE = 10;

    @BeforeEach
    public void before() {
        AlertConfig alertConfig = new AlertConfig();
        alertConfig.setSenderParallelism(QUEUE_SIZE);
        this.alertEventPendingQueue = new AlertEventPendingQueue(alertConfig);
    }

    @SneakyThrows
    @Test
    void put() {
        for (int i = 0; i < alertEventPendingQueue.capacity(); i++) {
            alertEventPendingQueue.put(new Alert());
        }

        CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
            try {
                alertEventPendingQueue.put(new Alert());
                System.out.println(alertEventPendingQueue.size());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        assertThrowsExactly(ConditionTimeoutException.class,
                () -> await()
                        .timeout(Duration.ofSeconds(2))
                        .until(completableFuture::isDone));

    }

    @Test
    void take() {
        CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
            try {
                alertEventPendingQueue.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        assertThrowsExactly(ConditionTimeoutException.class,
                () -> await()
                        .timeout(Duration.ofSeconds(2))
                        .until(completableFuture::isDone));
    }

    @SneakyThrows
    @Test
    void size() {
        for (int i = 0; i < alertEventPendingQueue.capacity(); i++) {
            alertEventPendingQueue.put(new Alert());
            assertThat(alertEventPendingQueue.size()).isEqualTo(i + 1);
        }
    }
}
