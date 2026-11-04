package io.yak.ops.alert.service;

import io.yak.ops.alert.metrics.AlertServerMetrics;
import io.yak.ops.dao.entity.Alert;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AlertEventLoop extends AbstractEventLoop<Alert> {

    private final AlertSender alertSender;

    public AlertEventLoop(AlertEventPendingQueue alertEventPendingQueue,
                          AlertSenderThreadPoolFactory alertSenderThreadPoolFactory,
                          AlertSender alertSender) {
        super("AlertEventLoop", alertSenderThreadPoolFactory.getThreadPool(), alertEventPendingQueue);
        this.alertSender = alertSender;
        AlertServerMetrics.registerPendingAlertGauge(this::getHandlingEventCount);
    }

    @Override
    public void handleEvent(Alert event) {
        alertSender.sendEvent(event);
    }

}
