package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.Schedule;
import io.yak.ops.dao.mapper.ScheduleMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.ScheduleDao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.NonNull;

@Repository
public class ScheduleDaoImpl extends BaseDao<Schedule, ScheduleMapper> implements ScheduleDao {

    public ScheduleDaoImpl(@NonNull ScheduleMapper scheduleMapper) {
        super(scheduleMapper);
    }

    @Override
    public Schedule queryByWorkflowDefinitionCode(long workflowDefinitionCode) {
        return mybatisMapper.queryByWorkflowDefinitionCode(workflowDefinitionCode);
    }

    @Override
    public List<Schedule> querySchedulerListByProjectName(String projectName) {
        return mybatisMapper.querySchedulerListByProjectName(projectName);
    }

    @Override
    public IPage<Schedule> queryByProjectAndWorkflowDefinitionCodePaging(IPage<Schedule> page,
                                                                         long projectCode,
                                                                         long workflowDefinitionCode,
                                                                         String searchVal) {
        return mybatisMapper.queryByProjectAndWorkflowDefinitionCodePaging(page, projectCode, workflowDefinitionCode,
                searchVal);
    }

    @Override
    public List<Schedule> querySchedulesByWorkflowDefinitionCodes(List<Long> workflowDefinitionCodes) {
        return mybatisMapper.querySchedulesByWorkflowDefinitionCodes(workflowDefinitionCodes);
    }

    @Override
    public List<Schedule> queryScheduleListByTenant(String tenantCode) {
        return mybatisMapper.queryScheduleListByTenant(tenantCode);
    }

    @Override
    public List<Schedule> queryScheduleByWorkerGroup(String workerGroupName) {
        return mybatisMapper.selectList(
                new QueryWrapper<Schedule>().lambda().eq(Schedule::getWorkerGroup, workerGroupName));
    }
}
