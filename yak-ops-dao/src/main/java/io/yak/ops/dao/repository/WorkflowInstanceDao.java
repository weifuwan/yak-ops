package io.yak.ops.dao.repository;

import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.model.WorkflowInstanceStatusCountDto;
import io.yak.ops.dao.model.WorkflowInstanceSummaryDto;
import io.yak.ops.plugin.task.api.model.DateInterval;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface WorkflowInstanceDao extends IDao<WorkflowInstance> {

    /**
     * insert or update work workflow instance to database
     *
     * @param workflowInstance workflowInstance
     */
    void upsertWorkflowInstance(WorkflowInstance workflowInstance);

    /**
     * Update workflow instance from originState to targetState
     */
    void updateWorkflowInstanceState(Integer workflowInstanceId,
                                     WorkflowExecutionStatus originState,
                                     WorkflowExecutionStatus targetState);

    void forceUpdateWorkflowInstanceState(Integer id, WorkflowExecutionStatus status);

    /**
     * find last scheduler workflow instance in the date interval
     *
     * @param workflowDefinitionCode definitionCode
     * @param taskDefinitionCode    definitionCode
     * @param dateInterval          dateInterval
     * @return workflow instance
     */
    WorkflowInstance queryLastSchedulerWorkflowInterval(Long workflowDefinitionCode, Long taskDefinitionCode,
                                                        DateInterval dateInterval);

    /**
     * find last manual workflow instance interval
     *
     * @param definitionCode workflow definition code
     * @param taskCode       taskCode
     * @param dateInterval   dateInterval
     * @return workflow instance
     */
    WorkflowInstance queryLastManualWorkflowInterval(Long definitionCode, Long taskCode, DateInterval dateInterval);

    WorkflowInstance queryLastRunningWorkflowInterval(Long definitionCode, DateInterval dateInterval);

    /**
     * query first schedule workflow instance
     *
     * @param definitionCode definitionCode
     * @return workflow instance
     */
    WorkflowInstanceSummaryDto queryFirstScheduleWorkflowInstance(Long definitionCode);

    /**
     * query first manual workflow instance
     *
     * @param definitionCode definitionCode
     * @return workflow instance
     */
    WorkflowInstanceSummaryDto queryFirstStartWorkflowInstance(Long definitionCode);

    WorkflowInstance querySubWorkflowInstanceByParentId(Integer workflowInstanceId, Integer taskInstanceId);

    List<WorkflowInstanceSummaryDto> queryByWorkflowCodeVersionStatus(Long workflowDefinitionCode,
                                                                      int workflowDefinitionVersion,
                                                                      int[] states);

    List<String> queryNeedFailoverMasters();

    /**
     * Query the workflow instances under the master that need to be failover.
     */
    List<WorkflowInstanceSummaryDto> queryNeedFailoverWorkflowInstances(String masterAddress);

    WorkflowInstance queryDetailById(int id);

    List<WorkflowInstanceStatusCountDto> countWorkflowInstanceStateByProjectCodes(Date startTime,
                                                                                  Date endTime,
                                                                                  Collection<Long> projectCodes);

    int updateWorkflowInstanceByTenantCode(String originTenantCode, String destTenantCode);

    int updateWorkflowInstanceByWorkerGroupName(String originWorkerGroupName, String destWorkerGroupName);

    List<WorkflowInstanceSummaryDto> queryByTenantCodeAndStatus(String tenantCode, int[] states);

    List<WorkflowInstanceSummaryDto> queryByWorkerGroupNameAndStatus(String workerGroupName, int[] states);

    List<WorkflowInstanceSummaryDto> queryTopNWorkflowInstance(int size,
                                                               Date startTime,
                                                               Date endTime,
                                                               WorkflowExecutionStatus status,
                                                               long projectCode);

    IPage<WorkflowInstanceSummaryDto> queryWorkflowInstanceListPaging(Page<WorkflowInstanceSummaryDto> page,
                                                                      Long projectCode,
                                                                      Long workflowDefinitionCode,
                                                                      String searchVal,
                                                                      String executorName,
                                                                      int[] statusArray,
                                                                      String host,
                                                                      Date startTime,
                                                                      Date endTime);

    List<WorkflowInstanceSummaryDto> queryByWorkflowDefinitionCodeAndStatus(Long workflowDefinitionCode, int[] states);

    List<WorkflowInstanceSummaryDto> queryByWorkflowDefinitionCode(Long workflowDefinitionCode, int size);

    List<WorkflowInstanceSummaryDto> queryByTriggerCode(Long triggerCode);
}
