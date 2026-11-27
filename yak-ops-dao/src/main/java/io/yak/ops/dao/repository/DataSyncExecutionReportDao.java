package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.DataSyncExecutionReport;

import java.util.List;

public interface DataSyncExecutionReportDao extends IDao<DataSyncExecutionReport> {

    DataSyncExecutionReport queryByExecutionId(String executionId);

    DataSyncExecutionReport queryLatestByTaskInstanceId(int taskInstanceId);

    List<DataSyncExecutionReport> queryLatestByTaskInstanceIds(List<Integer> taskInstanceIds);
}
