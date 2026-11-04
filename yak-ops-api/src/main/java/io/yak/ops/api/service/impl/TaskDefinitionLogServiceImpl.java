package io.yak.ops.api.service.impl;

import io.yak.ops.api.service.TaskDefinitionLogService;
import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.entity.WorkflowTaskRelationLog;
import io.yak.ops.dao.repository.TaskDefinitionLogDao;
import io.yak.ops.dao.repository.WorkflowTaskRelationLogDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskDefinitionLogServiceImpl implements TaskDefinitionLogService {

    @Autowired
    private WorkflowTaskRelationLogDao workflowTaskRelationLogDao;

    @Autowired
    private TaskDefinitionLogDao taskDefinitionLogDao;

    @Override
    public void deleteTaskByWorkflowDefinitionCode(long workflowDefinitionCode) {
        List<WorkflowTaskRelationLog> workflowTaskRelationLogList =
                workflowTaskRelationLogDao.queryByWorkflowDefinitionCode(workflowDefinitionCode);
        if (CollectionUtils.isEmpty(workflowTaskRelationLogList)) {
            return;
        }
        // delete task definition
        Set<Long> needToDeleteTaskDefinitionCodes = new HashSet<>();
        for (WorkflowTaskRelation workflowTaskRelation : workflowTaskRelationLogList) {
            needToDeleteTaskDefinitionCodes.add(workflowTaskRelation.getPreTaskCode());
            needToDeleteTaskDefinitionCodes.add(workflowTaskRelation.getPostTaskCode());
        }
        taskDefinitionLogDao.deleteByTaskDefinitionCodes(needToDeleteTaskDefinitionCodes);
        // delete task workflow relation
        workflowTaskRelationLogDao.deleteByWorkflowDefinitionCode(workflowDefinitionCode);
    }
}
