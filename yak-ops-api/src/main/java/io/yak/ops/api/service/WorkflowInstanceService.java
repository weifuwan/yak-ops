package io.yak.ops.api.service;

import io.yak.ops.api.dto.gantt.GanttDto;
import io.yak.ops.api.dto.workflowInstance.WorkflowInstanceTaskListDTO;
import io.yak.ops.api.dto.workflowInstance.WorkflowInstanceVariablesDTO;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.api.vo.WorkflowInstanceSummaryVO;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.model.WorkflowInstanceSummaryDto;

import java.util.List;
import java.util.Map;

public interface WorkflowInstanceService {

    /**
     * return top n SUCCESS workflow instance order by running time which started between startTime and endTime
     */
    List<WorkflowInstanceSummaryVO> queryTopNLongestRunningWorkflowInstance(User loginUser,
                                                                            long projectCode,
                                                                            int size,
                                                                            String startTime,
                                                                            String endTime);

    /**
     * query workflow instance by id
     *
     * @param loginUser   login user
     * @param projectCode project code
     * @param workflowInstanceId   workflow instance id
     * @return workflow instance detail
     */
    WorkflowInstance queryWorkflowInstanceById(User loginUser,
                                               long projectCode,
                                               Integer workflowInstanceId);

    /**
     * paging query workflow instance list, filtering according to project, workflow definition, time range, keyword, workflow status
     *
     * @param loginUser         login user
     * @param projectCode       project code
     * @param pageNo            page number
     * @param pageSize          page size
     * @param workflowDefinitionCode workflow definition code
     * @param searchVal         search value
     * @param stateType         state type
     * @param host              host
     * @param startDate         start time
     * @param endDate           end time
     * @param otherParamsJson   otherParamsJson handle other params
     * @return workflow instance list
     */
    Result<PageInfo<WorkflowInstanceSummaryVO>> queryWorkflowInstanceList(User loginUser,
                                                                          long projectCode,
                                                                          long workflowDefinitionCode,
                                                                          String startDate,
                                                                          String endDate,
                                                                          String searchVal,
                                                                          String executorName,
                                                                          WorkflowExecutionStatus stateType,
                                                                          String host,
                                                                          String otherParamsJson,
                                                                          Integer pageNo,
                                                                          Integer pageSize);

    /**
     * query task list by workflow instance id
     *
     * @param loginUser   login user
     * @param projectCode project code
     * @param workflowInstanceId   workflow instance id
     * @return task list for the workflow instance
     */
    WorkflowInstanceTaskListDTO queryTaskListByWorkflowInstanceId(User loginUser,
                                                                  long projectCode,
                                                                  Integer workflowInstanceId);

    /**
     * query sub workflow instance detail info by task id
     *
     * @param loginUser   login user
     * @param projectCode project code
     * @param taskId      task id
     * @return single-entry map keyed by {@code subWorkflowInstanceId}
     */
    Map<String, Integer> querySubWorkflowInstanceByTaskId(User loginUser,
                                                          long projectCode,
                                                          Integer taskId);

    /**
     * update workflow instance
     *
     * @param loginUser          login user
     * @param projectCode        project code
     * @param taskRelationJson   workflow task relation json
     * @param taskDefinitionJson taskDefinitionJson
     * @param workflowInstanceId  workflow instance id
     * @param scheduleTime       schedule time
     * @param syncDefine         sync define
     * @param globalParams       global params
     * @param locations          locations for nodes
     * @param timeout            timeout
     * @return updated workflow definition
     */
    WorkflowDefinition updateWorkflowInstance(User loginUser,
                                              long projectCode,
                                              Integer workflowInstanceId,
                                              String taskRelationJson,
                                              String taskDefinitionJson,
                                              String scheduleTime,
                                              Boolean syncDefine,
                                              String globalParams,
                                              String locations,
                                              int timeout);

    /**
     * query parent workflow instance detail info by sub workflow instance id
     *
     * @param loginUser   login user
     * @param projectCode project code
     * @param subId       sub workflow id
     * @return single-entry map keyed by {@code parentWorkflowInstance}
     */
    Map<String, Integer> queryParentInstanceBySubId(User loginUser,
                                                    long projectCode,
                                                    Integer subId);

    /**
     * delete workflow instance by id, at the same time，delete task instance and their mapping relation data
     *
     * @param loginUser         login user
     * @param workflowInstanceId workflow instance id
     */
    void deleteWorkflowInstanceById(User loginUser,
                                    Integer workflowInstanceId);

    /**
     * view workflow instance variables
     *
     * @param loginUser         login user
     * @param projectCode       project code
     * @param workflowInstanceId workflow instance id
     * @return variables data
     */
    WorkflowInstanceVariablesDTO viewVariables(User loginUser, long projectCode, Integer workflowInstanceId);

    /**
     * encapsulation gantt structure
     *
     * @param loginUser         login user
     * @param projectCode       project code
     * @param workflowInstanceId workflow instance id
     * @return gantt tree data
     * @throws Exception exception when json parse
     */
    GanttDto viewGantt(User loginUser, long projectCode, Integer workflowInstanceId) throws Exception;

    /**
     * query workflow instance by workflowDefinitionCode and stateArray
     *
     * @param workflowDefinitionCode workflowDefinitionCode
     * @param states                states array
     * @return workflow instance list
     */
    List<WorkflowInstanceSummaryDto> queryByWorkflowDefinitionCodeAndStatus(Long workflowDefinitionCode,
                                                                            int[] states);

    /**
     * query workflow instance by workflowDefinitionCode and stateArray
     *
     * @param workflowDefinitionCode    workflowDefinitionCode
     * @param workflowDefinitionVersion workflowDefinitionVersion
     * @param states                    states array
     * @return workflow instance list
     */
    List<WorkflowInstanceSummaryDto> queryByWorkflowCodeVersionStatus(Long workflowDefinitionCode,
                                                                      int workflowDefinitionVersion,
                                                                      int[] states);

    /**
     * query workflow instance by workflowDefinitionCode
     *
     * @param workflowDefinitionCode workflowDefinitionCode
     * @param size                  size
     * @return workflow instance list
     */
    List<WorkflowInstanceSummaryDto> queryByWorkflowDefinitionCode(Long workflowDefinitionCode,
                                                                   int size);

    /**
     * query workflow instance list by trigger code
     *
     * @param loginUser   login user
     * @param projectCode project code
     * @param triggerCode trigger code (nullable)
     * @return workflow instances triggered by the given trigger code
     */
    List<WorkflowInstanceSummaryVO> queryByTriggerCode(User loginUser, long projectCode, Long triggerCode);

    void deleteWorkflowInstanceByWorkflowDefinitionCode(long workflowDefinitionCode);

    void deleteWorkflowInstanceById(int workflowInstanceId);

}
