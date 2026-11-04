package io.yak.ops.server.master.engine.system;

import io.yak.ops.common.lifecycle.ServerLifeCycleManager;
import io.yak.ops.common.thread.BaseDaemonThread;
import io.yak.ops.common.thread.ThreadUtils;
import io.yak.ops.server.master.engine.system.event.AbstractSystemEvent;
import io.yak.ops.server.master.engine.system.event.ISystemEventHandler;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.StopWatch;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@SuppressWarnings({"unchecked", "rawtypes"})
public class SystemEventBusFireWorker extends BaseDaemonThread implements AutoCloseable {

    @Autowired
    private SystemEventBus systemEventBus;

    @Autowired
    private List<ISystemEventHandler> systemEventHandlers;

    private static boolean flag = false;

    public SystemEventBusFireWorker() {
        super("SystemEventBusFireWorker");
    }

    @Override
    public void start() {
        flag = true;
        super.start();
        log.info("SystemEventBusFireWorker started");
    }

    @Override
    public void run() {
        while (flag) {
            final AbstractSystemEvent systemEvent;
            try {
                systemEvent = systemEventBus.take();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                log.warn("SystemEventBusFireWorker has been interrupted", interruptedException);
                break;
            }
            if (ServerLifeCycleManager.isStopped()) {
                log.info("SystemEventBusFireWorker has been stopped");
                break;
            }
            try {
                fireSystemEvent(systemEvent);
            } catch (Exception ex) {
                // Put the event back to eventbus.
                systemEventBus.publish(systemEvent);
                log.error("Fire SystemEvent: {} failed", systemEvent, ex);
                ThreadUtils.sleep(10_000);
            }
        }
    }

    private void fireSystemEvent(final AbstractSystemEvent systemEvent) {
        final StopWatch stopWatch = StopWatch.createStarted();
        final List<ISystemEventHandler> matchedSystemEventHandlers = systemEventHandlers
                .stream()
                .filter(systemEventHandler -> systemEventHandler.matchState() == systemEvent.getEventType())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(matchedSystemEventHandlers)) {
            log.error("No matched SystemEventHandler for SystemEvent: {}", systemEvent);
            return;
        }
        matchedSystemEventHandlers.forEach(systemEventHandler -> systemEventHandler.handle(systemEvent));
        stopWatch.stop();
        log.info("Fire SystemEvent: {} cost: {} ms", systemEvent, stopWatch.getTime());
    }

    @Override
    public void close() {
        flag = false;
        log.info("SystemEventBusFireWorker closed");
    }
}
