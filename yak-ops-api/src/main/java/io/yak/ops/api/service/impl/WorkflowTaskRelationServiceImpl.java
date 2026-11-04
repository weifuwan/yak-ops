package io.yak.ops.api.service.impl;

import io.yak.ops.api.service.WorkflowTaskRelationService;
import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.repository.WorkflowTaskRelationDao;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WorkflowTaskRelationServiceImpl extends BaseServiceImpl implements WorkflowTaskRelationService {

    @Autowired
    private WorkflowTaskRelationDao workflowTaskRelationDao;

    @Override
    public List<WorkflowTaskRelation> queryByWorkflowDefinitionCode(long workflowDefinitionCode,
                                                                    int workflowDefinitionVersion) {
        return workflowTaskRelationDao.queryWorkflowTaskRelationsByWorkflowDefinitionCode(workflowDefinitionCode,
                workflowDefinitionVersion);
    }

    @Override
    public void deleteByWorkflowDefinitionCode(long workflowDefinitionCode, int workflowDefinitionVersion) {
        workflowTaskRelationDao.deleteByWorkflowDefinitionCodeAndVersion(workflowDefinitionCode,
                workflowDefinitionVersion);
    }

}
