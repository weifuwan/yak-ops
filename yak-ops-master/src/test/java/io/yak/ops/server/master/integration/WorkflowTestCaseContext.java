package io.yak.ops.server.master.integration;

import io.yak.ops.dao.entity.Environment;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskGroup;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.entity.WorkflowTaskRelation;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTestCaseContext {

    private Project project;

    private List<WorkflowDefinition> workflows;

    private List<WorkflowInstance> workflowInstances;

    private List<TaskInstance> taskInstances;

    private List<TaskDefinition> tasks;

    private List<WorkflowTaskRelation> taskRelations;

    private List<TaskGroup> taskGroups;

    private List<Environment> environments;

    public WorkflowDefinition getOneWorkflow() {
        if (CollectionUtils.isEmpty(workflows)) {
            throw new IllegalStateException("workflows is empty");
        }
        return workflows.get(0);
    }

    public WorkflowDefinition getWorkflow(String name) {
        if (CollectionUtils.isEmpty(workflows)) {
            throw new IllegalStateException("workflows is empty");
        }
        List<WorkflowDefinition> collect = workflows.stream()
                .filter(workflow -> workflow.getName().equals(name))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(collect)) {
            throw new IllegalStateException("Workflow with name " + name + " not found");
        }
        if (collect.size() > 1) {
            throw new IllegalStateException("Multiple workflows with name " + name + " found");
        }
        return collect.get(0);
    }
}
