package io.yak.ops.registry.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RegistryNodeType {

    FAILOVER_FINISH_NODES("FailoverFinishNodes", "/nodes/failover-finish-nodes"),

    GLOBAL_MASTER_FAILOVER_LOCK("GlobalMasterFailoverLock", "/lock/global-master-failover"),
    MASTER("Master", "/nodes/master"),
    MASTER_FAILOVER_LOCK("MasterFailoverLock", "/lock/master-failover"),
    MASTER_COORDINATOR("MasterCoordinator", "/nodes/master-coordinator"),
    MASTER_TASK_GROUP_COORDINATOR_LOCK("TaskGroupCoordinatorLock", "/lock/master-task-group-coordinator"),
    MASTER_SERIAL_COORDINATOR_LOCK("SerialWorkflowCoordinator", "/lock/master-serial-workflow-coordinator"),

    WORKER("Worker", "/nodes/worker"),

    ALERT_SERVER("AlertServer", "/nodes/alert-server"),
    ALERT_HA_LEADER("AlertHALeader", "/nodes/alert-server-ha-leader");

    private final String name;

    private final String registryPath;
}
