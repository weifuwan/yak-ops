package io.yak.ops.api.service;

import io.yak.ops.api.utils.Result;
import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.dao.entity.User;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

public interface TaskInstanceService {

    /**
     * query task list by project, workflow instance, task name, task start time, task end time, task status, keyword paging
     *
     * @param loginUser login user
     * @param projectCode project code
     * @param workflowInstanceId workflow instance id
     * @param searchVal search value
     * @param taskName task name
     * @param taskCode task code
     * @param stateType state type
     * @param host host
     * @param startDate start time
     * @param endDate end time
     * @param taskExecuteType task execute type
     * @param pageNo page number
     * @param pageSize page size
     * @return task list page
     */
    Result queryTaskListPaging(User loginUser,
                               long projectCode,
                               Integer workflowInstanceId,
                               String workflowInstanceName,
                               String workflowDefinitionName,
                               String taskName,
                               Long taskCode,
                               String executorName,
                               String startDate,
                               String endDate,
                               String searchVal,
                               TaskExecutionStatus stateType,
                               String host,
                               TaskExecuteType taskExecuteType,
                               Integer pageNo,
                               Integer pageSize);

    /**
     * change one task instance's state from failure to forced success
     *
     * @param loginUser login user
     * @param projectCode project code
     * @param taskInstanceId task instance id
     * @return the result code and msg
     */
    void forceTaskSuccess(User loginUser,
                          long projectCode,
                          Integer taskInstanceId);

    /**
     * task savepoint
     * @param loginUser
     * @param projectCode
     * @param taskInstanceId
     * @return
     */
    Result taskSavePoint(User loginUser, long projectCode, Integer taskInstanceId);

    /**
     * stop task
     * @param loginUser
     * @param projectCode
     * @param taskInstanceId
     * @return
     */
    Result stopTask(User loginUser, long projectCode, Integer taskInstanceId);

    void deleteByWorkflowInstanceId(Integer workflowInstanceId);
}
