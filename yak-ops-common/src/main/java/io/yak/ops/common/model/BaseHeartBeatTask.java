package io.yak.ops.common.model;

import io.yak.ops.common.thread.BaseDaemonThread;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseHeartBeatTask<T extends HeartBeat> extends BaseDaemonThread {

    private static final long DEFAULT_HEARTBEAT_SCAN_INTERVAL = 1_000L;

    private final String threadName;
    private final long heartBeatInterval;

    protected boolean runningFlag;

    protected long lastWriteTime = 0L;

    protected T lastHeartBeat = null;

    public BaseHeartBeatTask(String threadName, long heartBeatInterval) {
        super(threadName);
        this.threadName = threadName;
        this.heartBeatInterval = heartBeatInterval;
        this.runningFlag = true;
    }

    @Override
    public synchronized void start() {
        log.info("Starting {}...", threadName);
        super.start();
        log.info("Started {}, heartBeatInterval: {}...", threadName, heartBeatInterval);
    }

    @Override
    public void run() {
        while (runningFlag) {
            try {
                T heartBeat = getHeartBeat();
                // if first time or heartBeat status changed, write heartBeatInfo into registry
                if (System.currentTimeMillis() - lastWriteTime >= heartBeatInterval
                        || !lastHeartBeat.getServerStatus().equals(heartBeat.getServerStatus())) {
                    lastHeartBeat = heartBeat;
                    writeHeartBeat(heartBeat);
                    lastWriteTime = System.currentTimeMillis();
                }
            } catch (Exception ex) {
                log.error("{} task execute failed", threadName, ex);
            } finally {
                try {
                    Thread.sleep(DEFAULT_HEARTBEAT_SCAN_INTERVAL);
                } catch (InterruptedException e) {
                    handleInterruptException(e);
                }
            }
        }
    }

    public void shutdown() {
        runningFlag = false;
        log.warn("{} finished...", threadName);
    }

    private void handleInterruptException(InterruptedException ex) {
        log.warn("{} has been interrupted", threadName, ex);
        Thread.currentThread().interrupt();
    }

    public abstract T getHeartBeat();

    public abstract void writeHeartBeat(T heartBeat);
}
