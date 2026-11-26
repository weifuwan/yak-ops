package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.DataSyncRecoveryPoint;

import java.util.List;

public interface DataSyncRecoveryPointDao extends IDao<DataSyncRecoveryPoint> {

    DataSyncRecoveryPoint queryByTaskInstanceId(int taskInstanceId);

    List<DataSyncRecoveryPoint> queryAvailableByTaskInstanceIds(List<Integer> taskInstanceIds);

    boolean markUsed(long id, int workflowInstanceId);

    boolean markSuperseded(long id);
}
