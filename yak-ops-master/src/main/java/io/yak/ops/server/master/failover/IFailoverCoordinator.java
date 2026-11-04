package io.yak.ops.server.master.failover;

import io.yak.ops.server.master.engine.system.event.GlobalMasterFailoverEvent;
import io.yak.ops.server.master.engine.system.event.MasterFailoverEvent;
import io.yak.ops.server.master.engine.system.event.WorkerFailoverEvent;

/**
 * Failover coordinator, responsible for do some failover work when the master or worker server is removed from the cluster.
 * <p> The failover work is aim to make the system continue to work normally after the server is removed.
 */
public interface IFailoverCoordinator {

    /**
     * Global failover, will find out the workflows which should be failover in the global system, then failover them.
     * <p> The global failover is very slow since it will need to scan the whole workflows in the system, it should only be called when the server first startup.
     * And it shouldn't be called in the main thread, since this method might be blocked for a long time.
     */
    void globalMasterFailover(final GlobalMasterFailoverEvent globalMasterFailoverEvent);

    /**
     * Failover master server, will find out the workflows that are running on the failed master server, then failover them.
     * <p> This method is called when a master server is removed from the cluster.
     */
    void failoverMaster(final MasterFailoverEvent masterFailoverEvent);

    /**
     * Failover worker server, will find out the tasks which has been dispatched to the crashed worker and running
     * on the current master, then failover them.
     *
     * <p> This method is called when a worker server is removed from the cluster.
     */
    void failoverWorker(final WorkerFailoverEvent workerFailoverEvent);

    /**
     * Once the failover is finished, we will persist a mark in registry center.
     * <p> This method is used to clean the finished marks which has expired to avoid the data accumulation.
     */
    void cleanHistoryFailoverFinishedMarks();
}
