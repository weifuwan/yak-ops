package io.yak.ops.dao.repository.impl;

import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.mapper.TaskInstanceMapper;
import io.yak.ops.dao.mapper.WorkflowInstanceMapper;
import io.yak.ops.dao.model.TaskInstanceStatusCountDto;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Task Instance DAO implementation
 */
@Repository
@Slf4j
public class TaskInstanceDaoImpl extends BaseDao<TaskInstance, TaskInstanceMapper> implements TaskInstanceDao {

    @Autowired
    private WorkflowInstanceMapper workflowInstanceMapper;

    public TaskInstanceDaoImpl(@NonNull TaskInstanceMapper taskInstanceMapper) {
        super(taskInstanceMapper);
    }

    @Override
    public boolean upsertTaskInstance(TaskInstance taskInstance) {
        if (taskInstance.getId() != null) {
            return updateById(taskInstance);
        } else {
            return insert(taskInstance) > 0;
        }
    }

    @Override
    public boolean submitTaskInstanceToDB(TaskInstance taskInstance, WorkflowInstance workflowInstance) {
        WorkflowExecutionStatus processInstanceState = workflowInstance.getState();
        if (processInstanceState.isFinalState() || processInstanceState == WorkflowExecutionStatus.READY_STOP) {
            log.warn("processInstance: {} state was: {}, skip submit this task, taskCode: {}",
                    workflowInstance.getId(),
                    processInstanceState,
                    taskInstance.getTaskCode());
            return false;
        }
        if (processInstanceState == WorkflowExecutionStatus.READY_PAUSE) {
            taskInstance.setState(TaskExecutionStatus.PAUSE);
        }
        taskInstance.setExecutorId(workflowInstance.getExecutorId());
        taskInstance.setExecutorName(workflowInstance.getExecutorName());
        taskInstance.setState(getSubmitTaskState(taskInstance, workflowInstance));
        if (taskInstance.getSubmitTime() == null) {
            taskInstance.setSubmitTime(new Date());
        }
        if (taskInstance.getFirstSubmitTime() == null) {
            taskInstance.setFirstSubmitTime(taskInstance.getSubmitTime());
        }
        return upsertTaskInstance(taskInstance);
    }

    @Override
    public void markTaskInstanceInvalid(List<TaskInstance> taskInstances) {
        if (CollectionUtils.isEmpty(taskInstances)) {
            return;
        }
        for (TaskInstance taskInstance : taskInstances) {
            taskInstance.setFlag(Flag.NO);
            mybatisMapper.updateById(taskInstance);
        }
    }

    private TaskExecutionStatus getSubmitTaskState(TaskInstance taskInstance, WorkflowInstance workflowInstance) {
        TaskExecutionStatus state = taskInstance.getState();
        if (state == TaskExecutionStatus.RUNNING_EXECUTION
                || state == TaskExecutionStatus.DELAY_EXECUTION
                || state == TaskExecutionStatus.KILL
                || state == TaskExecutionStatus.DISPATCH) {
            return state;
        }

        if (workflowInstance.getState() == WorkflowExecutionStatus.READY_PAUSE) {
            state = TaskExecutionStatus.PAUSE;
        } else if (workflowInstance.getState() == WorkflowExecutionStatus.READY_STOP
                || !checkProcessStrategy(taskInstance, workflowInstance)) {
            state = TaskExecutionStatus.KILL;
        } else {
            state = TaskExecutionStatus.SUBMITTED_SUCCESS;
        }
        return state;
    }

    private boolean checkProcessStrategy(TaskInstance taskInstance, WorkflowInstance workflowInstance) {
        FailureStrategy failureStrategy = workflowInstance.getFailureStrategy();
        if (failureStrategy == FailureStrategy.CONTINUE) {
            return true;
        }
        List<TaskInstance> taskInstances =
                this.queryValidTaskListByWorkflowInstanceId(taskInstance.getWorkflowInstanceId());

        for (TaskInstance task : taskInstances) {
            if (task.getState() == TaskExecutionStatus.FAILURE
                    && task.getRetryTimes() >= task.getMaxRetryTimes()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<TaskInstance> queryValidTaskListByWorkflowInstanceId(Integer processInstanceId) {
        return mybatisMapper.findValidTaskListByWorkflowInstanceId(processInstanceId, Flag.YES);
    }

    @Override
    public TaskInstance queryByWorkflowInstanceIdAndTaskCode(Integer workflowInstanceId, Long taskCode) {
        return mybatisMapper.queryByInstanceIdAndCode(workflowInstanceId, taskCode);
    }

    @Override
    public List<TaskInstance> queryPreviousTaskListByWorkflowInstanceId(Integer workflowInstanceId) {
        WorkflowInstance workflowInstance = workflowInstanceMapper.selectById(workflowInstanceId);
        return mybatisMapper.findValidTaskListByWorkflowInstanceId(workflowInstanceId, Flag.NO);
    }

    @Override
    public void deleteByWorkflowInstanceId(int workflowInstanceId) {
        mybatisMapper.deleteByWorkflowInstanceId(workflowInstanceId);
    }

    @Override
    public List<TaskInstance> queryByWorkflowInstanceId(Integer workflowInstanceId) {
        return mybatisMapper.findByWorkflowInstanceId(workflowInstanceId);
    }

    @Override
    public List<TaskInstance> queryLastTaskInstanceListIntervalInWorkflowInstance(Integer workflowInstanceId,
                                                                                  Set<Long> taskCodes) {
        return mybatisMapper.findLastTaskInstances(workflowInstanceId, taskCodes);
    }

    @Override
    public TaskInstance queryLastTaskInstanceIntervalInWorkflowInstance(Integer workflowInstanceId, long depTaskCode) {
        return mybatisMapper.findLastTaskInstance(workflowInstanceId, depTaskCode);
    }

    @Override
    public void updateTaskInstanceState(Integer taskInstanceId,
                                        TaskExecutionStatus originState,
                                        TaskExecutionStatus targetState) {
        mybatisMapper.updateTaskInstanceState(taskInstanceId, originState.getCode(), targetState.getCode());
    }

    @Override
    public List<TaskInstanceStatusCountDto> countTaskInstanceStateByProjectCodes(Date startTime,
                                                                                 Date endTime,
                                                                                 Collection<Long> projectCodes) {
        return mybatisMapper.countTaskInstanceStateByProjectCodes(startTime, endTime, projectCodes);
    }

    @Override
    public List<TaskInstance> queryByWorkflowInstanceIdsAndTaskCodes(List<Integer> workflowInstanceIds,
                                                                     List<Long> taskCodes) {
        return mybatisMapper.queryByWorkflowInstanceIdsAndTaskCodes(workflowInstanceIds, taskCodes);
    }

    @Override
    public IPage<TaskInstance> queryTaskInstanceListPaging(IPage<TaskInstance> page,
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
                                                           Date endTime) {
        return mybatisMapper.queryTaskInstanceListPaging(page, projectCode, workflowInstanceId, workflowInstanceName,
                searchVal, taskName, taskCode, executorName, statusArray, host, taskExecuteType, startTime, endTime);
    }

    @Override
    public IPage<TaskInstance> queryStreamTaskInstanceListPaging(IPage<TaskInstance> page,
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
                                                                 Date endTime) {
        return mybatisMapper.queryStreamTaskInstanceListPaging(page, projectCode, workflowDefinitionName, searchVal,
                taskName, taskCode, executorName, statusArray, host, taskExecuteType, startTime, endTime);
    }
}
