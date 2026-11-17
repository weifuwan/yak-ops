package io.yak.ops.agent.service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import io.yak.ops.agent.config.AgentConfiguration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bounded worker pool that runs turns. Rejections are surfaced to the caller instead of
 * queueing without limit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
public class AgentTurnExecutor {

    private final AgentConfiguration configuration;

    private ThreadPoolExecutor executor;

    @PostConstruct
    void init() {
        int size = Math.max(1, configuration.getTurnPoolSize());
        executor = new ThreadPoolExecutor(size, size, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(size * 8),
                runnable -> {
                    Thread thread = new Thread(runnable, "agent-turn-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        log.info("Agent turn executor started with {} worker(s)", size);
    }

    public boolean execute(Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException ex) {
            return false;
        }
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
