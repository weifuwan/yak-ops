package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.Schedule;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface ScheduleDao extends IDao<Schedule> {

    Schedule queryByWorkflowDefinitionCode(long workflowDefinitionCode);

    List<Schedule> querySchedulerListByProjectName(String projectName);

    IPage<Schedule> queryByProjectAndWorkflowDefinitionCodePaging(IPage<Schedule> page,
                                                                  long projectCode,
                                                                  long workflowDefinitionCode,
                                                                  String searchVal);

    List<Schedule> querySchedulesByWorkflowDefinitionCodes(List<Long> workflowDefinitionCodes);

    List<Schedule> queryScheduleListByTenant(String tenantCode);

    List<Schedule> queryScheduleByWorkerGroup(String workerGroupName);
}
