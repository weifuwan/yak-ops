package io.yak.ops.alert.service;

import io.yak.ops.common.thread.BaseDaemonThread;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractEventFetcher<T> extends BaseDaemonThread implements EventFetcher<T> {

    protected static final int FETCH_SIZE = 100;

    protected static final long FETCH_INTERVAL = 5_000;

    protected final AlertHAServer alertHAServer;

    private final EventPendingQueue<T> eventPendingQueue;

    private final AtomicBoolean runningFlag = new AtomicBoolean(false);

    private Integer eventOffset;

    protected AbstractEventFetcher(String fetcherName,
                                   AlertHAServer alertHAServer,
                                   EventPendingQueue<T> eventPendingQueue) {
        super(fetcherName);
        this.alertHAServer = alertHAServer;
        this.eventPendingQueue = eventPendingQueue;
        this.eventOffset = -1;
    }

    @Override
    public synchronized void start() {
        if (!runningFlag.compareAndSet(false, true)) {
            throw new IllegalArgumentException("AlertEventFetcher is already started");
        }
        log.info("AlertEventFetcher starting...");
        super.start();
        log.info("AlertEventFetcher started...");
    }

    @Override
    public void run() {
        while (runningFlag.get()) {
            try {
                if (!alertHAServer.isActive()) {
                    log.debug("The current node is not active, will not loop Alert");
                    Thread.sleep(FETCH_INTERVAL);
                    continue;
                }
                List<T> pendingEvents = fetchPendingEvent(eventOffset);
                if (CollectionUtils.isEmpty(pendingEvents)) {
                    log.debug("No pending events found");
                    Thread.sleep(FETCH_INTERVAL);
                    continue;
                }
                for (T alert : pendingEvents) {
                    eventPendingQueue.put(alert);
                }
                eventOffset = Math.max(eventOffset,
                        pendingEvents.stream().map(this::getEventOffset).max(Integer::compareTo).get());
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.error("AlertEventFetcher error", ex);
            }
        }
    }

    protected abstract int getEventOffset(T event);

    @Override
    public void shutdown() {
        if (!runningFlag.compareAndSet(true, false)) {
            log.warn("The AlertEventFetcher is not started");
        }
    }

}
