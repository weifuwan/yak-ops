package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.DataSyncExecutionReport;
import io.yak.ops.dao.mapper.DataSyncExecutionReportMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.DataSyncExecutionReportDao;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class DataSyncExecutionReportDaoImpl
        extends BaseDao<DataSyncExecutionReport, DataSyncExecutionReportMapper>
        implements DataSyncExecutionReportDao {

    public DataSyncExecutionReportDaoImpl(@NonNull DataSyncExecutionReportMapper mapper) {
        super(mapper);
    }

    @Override
    public DataSyncExecutionReport queryByExecutionId(String executionId) {
        return mybatisMapper.queryByExecutionId(executionId);
    }

    @Override
    public DataSyncExecutionReport queryLatestByTaskInstanceId(int taskInstanceId) {
        return mybatisMapper.queryLatestByTaskInstanceId(taskInstanceId);
    }
}
