package io.yak.ops.alert.service;

import io.yak.ops.alert.config.AlertConfig;
import io.yak.ops.alert.metrics.AlertServerMetrics;
import io.yak.ops.dao.entity.Alert;

import org.springframework.stereotype.Component;

@Component
public class AlertEventPendingQueue extends AbstractEventPendingQueue<Alert> {

    public AlertEventPendingQueue(AlertConfig alertConfig) {
        super(alertConfig.getSenderParallelism() * 3 + 1);
        AlertServerMetrics.registerPendingAlertGauge(this::size);
    }
}
