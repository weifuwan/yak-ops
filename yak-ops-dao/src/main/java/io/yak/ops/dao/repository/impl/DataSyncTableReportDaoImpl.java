package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.DataSyncTableReport;
import io.yak.ops.dao.mapper.DataSyncTableReportMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.DataSyncTableReportDao;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class DataSyncTableReportDaoImpl
        extends BaseDao<DataSyncTableReport, DataSyncTableReportMapper>
        implements DataSyncTableReportDao {

    public DataSyncTableReportDaoImpl(@NonNull DataSyncTableReportMapper mapper) {
        super(mapper);
    }

    @Override
    public List<DataSyncTableReport> queryByExecutionId(String executionId) {
        return mybatisMapper.queryByExecutionId(executionId);
    }

    @Override
    public int clearSnapshotFields(
            Long id, long sequence, long collectedAt, Date updateTime, boolean finalReport) {
        return mybatisMapper.clearSnapshotFields(id, sequence, collectedAt, updateTime, finalReport);
    }

    @Override
    public DataSyncTableReport queryByExecutionIdAndTableKey(String executionId, String tableKey) {
        return mybatisMapper.queryByExecutionIdAndTableKey(executionId, tableKey);
    }
}
