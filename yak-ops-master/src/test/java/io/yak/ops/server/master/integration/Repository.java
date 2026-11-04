package io.yak.ops.server.master.integration;

import io.yak.ops.dao.entity.Alert;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.mapper.AlertMapper;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Repository {

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Autowired
    private TaskInstanceDao taskInstanceDao;

    @Autowired
    private AlertMapper alertMapper;

    /**
     * Return the list of process instances for a given workflow definition in ascending order of their IDs.
     */
    public List<WorkflowInstance> queryWorkflowInstance(final WorkflowDefinition workflowDefinition) {
        return workflowInstanceDao.queryAll()
                .stream()
                .filter(workflowInstance -> workflowInstance.getWorkflowDefinitionCode()
                        .equals(workflowDefinition.getCode()))
                .filter(workflowInstance -> workflowInstance.getWorkflowDefinitionVersion() == workflowDefinition
                        .getVersion())
                .sorted(Comparator.comparingInt(WorkflowInstance::getId))
                .collect(Collectors.toList());
    }

    public WorkflowInstance queryWorkflowInstance(final Integer workflowInstanceId) {
        return workflowInstanceDao.queryById(workflowInstanceId);
    }

    public List<WorkflowInstance> queryAllWorkflowInstance() {
        return workflowInstanceDao.queryAll();
    }

    /**
     * Return the list of task instances for a given workflow definition in ascending order of their IDs.
     */
    public List<TaskInstance> queryTaskInstance(final WorkflowDefinition workflowDefinition) {
        return queryWorkflowInstance(workflowDefinition)
                .stream()
                .flatMap(workflowInstance -> taskInstanceDao.queryByWorkflowInstanceId(workflowInstance.getId())
                        .stream())
                .sorted(Comparator.comparingInt(TaskInstance::getId))
                .collect(Collectors.toList());
    }

    /**
     * Return the list of task instances for a given workflow definition in ascending order of their IDs.
     */
    public List<TaskInstance> queryTaskInstance(final Integer workflowInstanceId) {
        return taskInstanceDao.queryByWorkflowInstanceId(workflowInstanceId)
                .stream()
                .sorted(Comparator.comparingInt(TaskInstance::getId))
                .collect(Collectors.toList());
    }

    public List<TaskInstance> queryAllTaskInstance() {
        return taskInstanceDao.queryAll();
    }

    /**
     * Return the list of alert for a given workflow instance in ascending order of their IDs.
     */
    public List<Alert> queryAlert(final Integer workflowInstanceId) {
        return alertMapper.selectByWorkflowInstanceId(workflowInstanceId)
                .stream()
                .sorted(Comparator.comparingInt(Alert::getId))
                .collect(Collectors.toList());
    }
}
