package io.yak.ops.server.master.engine.system.event;

public enum SystemEventType {

    /**
     * Global master failover used to scan the whole system and do master failover
     */
    GLOBAL_MASTER_FAILOVER,
    /**
     * Master failover used to do master failover for a specific master
     */
    MASTER_FAILOVER,
    /**
     * Master failover used to do worker failover for a specific worker
     */
    WORKER_FAILOVER,

}
