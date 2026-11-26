package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.DataSyncExecutionReport;

public interface DataSyncExecutionReportDao extends IDao<DataSyncExecutionReport> {

    DataSyncExecutionReport queryByExecutionId(String executionId);

    DataSyncExecutionReport queryLatestByTaskInstanceId(int taskInstanceId);
}
