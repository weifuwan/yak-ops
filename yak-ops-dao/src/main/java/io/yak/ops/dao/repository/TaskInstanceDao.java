package io.yak.ops.dao.repository;

import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.model.TaskInstanceStatusCountDto;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface TaskInstanceDao extends IDao<TaskInstance> {

    List<TaskInstance> queryLatestByTaskCodes(long projectCode, String taskType,
                                              List<Long> taskCodes, int[] states);

    /**
     * Update or Insert task instance to DB.
     * ID is null -> Insert
     * ID is not null -> Update
     *
     * @param taskInstance task instance
     * @return result
     */
    boolean upsertTaskInstance(TaskInstance taskInstance);

    /**
     * Submit a task instance to DB.
     *
     * @param taskInstance    task instance
     * @param workflowInstance workflow instance
     * @return task instance
     */
    boolean submitTaskInstanceToDB(TaskInstance taskInstance, WorkflowInstance workflowInstance);

    /**
     * Mark the task instance as invalid
     */
    void markTaskInstanceInvalid(List<TaskInstance> taskInstances);

    /**
     * Query list of valid task instance by workflow instance id
     *
     * @param workflowInstanceId workflowInstanceId
     * @return list of valid task instance
     */
    List<TaskInstance> queryValidTaskListByWorkflowInstanceId(Integer workflowInstanceId);

    /**
     * Query list of task instance by workflow instance id and task code
     *
     * @param workflowInstanceId workflowInstanceId
     * @param taskCode          task code
     * @return list of valid task instance
     */
    TaskInstance queryByWorkflowInstanceIdAndTaskCode(Integer workflowInstanceId, Long taskCode);

    /**
     * find previous task list by work workflow id
     *
     * @param workflowInstanceId workflowInstanceId
     * @return task instance list
     */
    List<TaskInstance> queryPreviousTaskListByWorkflowInstanceId(Integer workflowInstanceId);

    void deleteByWorkflowInstanceId(int workflowInstanceId);

    List<TaskInstance> queryByWorkflowInstanceId(Integer workflowInstanceId);

    /**
     * find last task instance list corresponding to taskCodes in the date interval
     *
     * @param workflowInstanceId Task's parent workflow instance id
     * @param taskCodes         taskCodes
     * @return task instance list
     */
    List<TaskInstance> queryLastTaskInstanceListIntervalInWorkflowInstance(Integer workflowInstanceId,
                                                                           Set<Long> taskCodes);

    /**
     * find last task instance corresponding to taskCode in the date interval
     *
     * @param workflowInstanceId Task's parent workflow instance id
     * @param depTaskCode       taskCode
     * @return task instance
     */
    TaskInstance queryLastTaskInstanceIntervalInWorkflowInstance(Integer workflowInstanceId,
                                                                 long depTaskCode);

    void updateTaskInstanceState(Integer taskInstanceId, TaskExecutionStatus originState,
                                 TaskExecutionStatus targetState);

    List<TaskInstanceStatusCountDto> countTaskInstanceStateByProjectCodes(Date startTime,
                                                                          Date endTime,
                                                                          Collection<Long> projectCodes);

    List<TaskInstance> queryByWorkflowInstanceIdsAndTaskCodes(List<Integer> workflowInstanceIds,
                                                              List<Long> taskCodes);

    IPage<TaskInstance> queryByProjectAndTaskType(IPage<TaskInstance> page, long projectCode,
                                                  String taskType, Long taskCode, int[] states);

    IPage<TaskInstance> queryTaskInstanceListPaging(IPage<TaskInstance> page,
                                                    Long projectCode,
                                                    Integer workflowInstanceId,
                                                    String workflowInstanceName,
                                                    String searchVal,
                                                    String taskName,
                                                    Long taskCode,
                                                    String executorName,
                                                    int[] statusArray,
                                                    String host,
                                                    TaskExecuteType taskExecuteType,
                                                    Date startTime,
                                                    Date endTime);

    IPage<TaskInstance> queryStreamTaskInstanceListPaging(IPage<TaskInstance> page,
                                                          Long projectCode,
                                                          String workflowDefinitionName,
                                                          String searchVal,
                                                          String taskName,
                                                          Long taskCode,
                                                          String executorName,
                                                          int[] statusArray,
                                                          String host,
                                                          TaskExecuteType taskExecuteType,
                                                          Date startTime,
                                                          Date endTime);
}
