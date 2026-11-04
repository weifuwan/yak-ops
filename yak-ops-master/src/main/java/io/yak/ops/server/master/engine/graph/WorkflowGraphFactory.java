package io.yak.ops.server.master.engine.graph;

import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.repository.TaskDefinitionLogDao;
import io.yak.ops.service.process.ProcessService;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkflowGraphFactory {

    @Autowired
    private ProcessService processService;

    @Autowired
    private TaskDefinitionLogDao taskDefinitionLogDao;

    public IWorkflowGraph createWorkflowGraph(WorkflowDefinition workflowDefinition) {

        List<WorkflowTaskRelation> workflowTaskRelations = processService.findRelationByCode(
                workflowDefinition.getCode(),
                workflowDefinition.getVersion());

        List<TaskDefinition> taskDefinitions = taskDefinitionLogDao.queryTaskDefineLogList(workflowTaskRelations)
                .stream()
                .map(TaskDefinition.class::cast)
                .collect(Collectors.toList());
        return new WorkflowGraph(workflowTaskRelations, taskDefinitions);
    }

}
