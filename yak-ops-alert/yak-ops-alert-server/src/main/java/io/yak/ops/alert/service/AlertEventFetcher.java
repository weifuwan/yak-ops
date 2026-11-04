package io.yak.ops.alert.service;

import io.yak.ops.dao.AlertDao;
import io.yak.ops.dao.entity.Alert;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AlertEventFetcher extends AbstractEventFetcher<Alert> {

    private final AlertDao alertDao;

    public AlertEventFetcher(AlertHAServer alertHAServer,
                             AlertDao alertDao,
                             AlertEventPendingQueue alertEventPendingQueue) {
        super("AlertEventFetcher", alertHAServer, alertEventPendingQueue);
        this.alertDao = alertDao;
    }

    @Override
    @Transactional
    public List<Alert> fetchPendingEvent(int eventOffset) {
        // We use transaction here to ensure that if mysql is configured at master/slave mode, this query will be routed
        // to the master db.
        // Avoid we query from the slave and find the data is not the latest.
        return alertDao.listPendingAlerts(eventOffset);
    }

    @Override
    protected int getEventOffset(Alert event) {
        return event.getId();
    }
}
