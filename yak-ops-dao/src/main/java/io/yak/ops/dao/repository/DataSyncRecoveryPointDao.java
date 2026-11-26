package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.DataSyncRecoveryPoint;

import java.util.List;

public interface DataSyncRecoveryPointDao extends IDao<DataSyncRecoveryPoint> {

    /** Latest recovery point of any state, ordered by completion time and creation time. */
    DataSyncRecoveryPoint queryByTaskInstanceId(int taskInstanceId);

    /** Latest usable recovery point for RESUME / RECOVER_FAILURE. */
    DataSyncRecoveryPoint queryLatestAvailableByTaskInstanceId(int taskInstanceId);

    List<DataSyncRecoveryPoint> queryByTaskInstanceIds(List<Integer> taskInstanceIds);

    boolean insertIfAbsent(DataSyncRecoveryPoint point);

    /** Persists a STOP Savepoint intent before the Worker RPC is invoked. */
    boolean insertPending(DataSyncRecoveryPoint point);

    boolean markAvailable(long id, String restorePath, java.util.Date completedAt);

    boolean markFailed(long id);

    boolean markUsed(long id, int workflowInstanceId);

    /** Links a restored execution to the recovery point after its new task instance is created. */
    boolean markUsedByInstance(String recoveryPointId, int instanceId, int workflowInstanceId);

    boolean markSuperseded(long id);

    boolean markAllAvailableSuperseded(int taskInstanceId);

    boolean supersedePreviousCheckpoints(int taskInstanceId, String executionId, String recoveryPointId);
}
