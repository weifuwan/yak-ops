package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.DependentSimplifyDefinition;
import io.yak.ops.dao.entity.ProjectWorkflowDefinitionCount;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.dao.model.PageListingResult;
import io.yak.ops.dao.model.WorkflowDefinitionCountDto;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.NonNull;

@Repository
public class WorkflowDefinitionDaoImpl extends BaseDao<WorkflowDefinition, WorkflowDefinitionMapper>
        implements
            WorkflowDefinitionDao {

    public WorkflowDefinitionDaoImpl(@NonNull WorkflowDefinitionMapper workflowDefinitionMapper) {
        super(workflowDefinitionMapper);
    }

    @Override
    public PageListingResult<WorkflowDefinition> listingWorkflowDefinition(int pageNumber, int pageSize,
                                                                           String searchVal,
                                                                           int userId, long projectCode) {
        Page<WorkflowDefinition> page = new Page<>(pageNumber, pageSize);
        IPage<WorkflowDefinition> processDefinitions =
                mybatisMapper.queryDefineListPaging(page, searchVal, userId, projectCode);

        return PageListingResult.<WorkflowDefinition>builder()
                .totalCount(processDefinitions.getTotal())
                .currentPage(pageNumber)
                .pageSize(pageSize)
                .records(processDefinitions.getRecords())
                .build();
    }

    @Override
    public Optional<WorkflowDefinition> queryByCode(long code) {
        return Optional.ofNullable(mybatisMapper.queryByCode(code));
    }

    @Override
    public void lockByCode(long code) {
        if (mybatisMapper.lockByCode(code) == null) {
            throw new IllegalArgumentException("Workflow definition not found: " + code);
        }
    }

    @Override
    public void deleteByWorkflowDefinitionCode(long workflowDefinitionCode) {
        mybatisMapper.deleteByCode(workflowDefinitionCode);
    }

    @Override
    public List<WorkflowDefinition> queryByCodes(Collection<Long> workflowDefinitionCodes) {
        if (CollectionUtils.isEmpty(workflowDefinitionCodes)) {
            return Collections.emptyList();
        }
        return mybatisMapper.queryByCodes(workflowDefinitionCodes);
    }

    @Override
    public WorkflowDefinition queryByDefineName(long projectCode, String workflowDefinitionName) {
        return mybatisMapper.queryByDefineName(projectCode, workflowDefinitionName);
    }

    @Override
    public WorkflowDefinition verifyByDefineName(long projectCode, String workflowDefinitionName) {
        return mybatisMapper.verifyByDefineName(projectCode, workflowDefinitionName);
    }

    @Override
    public List<WorkflowDefinition> queryAllDefinitionList(long projectCode) {
        return mybatisMapper.queryAllDefinitionList(projectCode);
    }

    @Override
    public List<DependentSimplifyDefinition> queryDefinitionListByProjectCodeAndWorkflowDefinitionCodes(long projectCode,
                                                                                                        Collection<Long> codes) {
        return mybatisMapper.queryDefinitionListByProjectCodeAndWorkflowDefinitionCodes(projectCode, codes);
    }

    @Override
    public List<WorkflowDefinitionCountDto> countDefinitionByProjectCodes(Collection<Long> projectCodes) {
        return mybatisMapper.countDefinitionByProjectCodes(projectCodes);
    }

    @Override
    public List<Long> queryDefinitionCodeListByProjectCodes(List<Long> projectCodes) {
        return mybatisMapper.queryDefinitionCodeListByProjectCodes(projectCodes);
    }

    @Override
    public List<ProjectWorkflowDefinitionCount> queryProjectWorkflowDefinitionCountByProjectCodes(List<Long> projectCodes) {
        return mybatisMapper.queryProjectWorkflowDefinitionCountByProjectCodes(projectCodes);
    }
}
