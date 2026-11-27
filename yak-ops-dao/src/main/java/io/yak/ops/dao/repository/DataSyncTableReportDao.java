package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.DataSyncTableReport;

import java.util.Date;
import java.util.List;

public interface DataSyncTableReportDao extends IDao<DataSyncTableReport> {

    int clearSnapshotFields(
            Long id, long sequence, long collectedAt, Date updateTime, boolean finalReport);

    List<DataSyncTableReport> queryByExecutionId(String executionId);

    DataSyncTableReport queryByExecutionIdAndTableKey(String executionId, String tableKey);
}
