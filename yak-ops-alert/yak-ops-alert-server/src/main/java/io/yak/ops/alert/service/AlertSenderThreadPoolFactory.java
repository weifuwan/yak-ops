package io.yak.ops.alert.service;

import io.yak.ops.alert.config.AlertConfig;
import io.yak.ops.common.thread.ThreadUtils;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.stereotype.Component;

@Component
public class AlertSenderThreadPoolFactory {

    private final ThreadPoolExecutor threadPool;

    public AlertSenderThreadPoolFactory(AlertConfig alertConfig) {
        this.threadPool = ThreadUtils.newDaemonFixedThreadExecutor("AlertSenderThread",
                alertConfig.getSenderParallelism());
    }

    public ThreadPoolExecutor getThreadPool() {
        return threadPool;
    }

}
