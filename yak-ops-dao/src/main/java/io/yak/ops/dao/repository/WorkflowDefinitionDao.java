package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.DependentSimplifyDefinition;
import io.yak.ops.dao.entity.ProjectWorkflowDefinitionCount;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.model.PageListingResult;
import io.yak.ops.dao.model.WorkflowDefinitionCountDto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

public interface WorkflowDefinitionDao extends IDao<WorkflowDefinition> {

    /**
     * Listing the workflow definition belongs to the given userId and projectCode.
     * If the searchValue is not null, will used at workflowDefinitionName or workflowDefinitionDescription.
     */
    // todo: Right now this method will use fuzzy query at searchVal, this will be very slow if there are exist a lot of
    // workflowDefinition belongs to the target user/project.
    PageListingResult<WorkflowDefinition> listingWorkflowDefinition(
                                                                    int pageNumber,
                                                                    int pageSize,
                                                                    @Nullable String searchVal,
                                                                    int userId,
                                                                    long projectCode);

    Optional<WorkflowDefinition> queryByCode(long code);

    boolean tryReserveStart(long code, String requestId);

    void releaseStart(long code, String requestId);

    boolean clearExpiredStart(long code, java.util.Date expiration);

    void deleteByWorkflowDefinitionCode(long workflowDefinitionCode);

    List<WorkflowDefinition> queryByCodes(Collection<Long> workflowDefinitionCodes);

    WorkflowDefinition queryByDefineName(long projectCode, String workflowDefinitionName);

    WorkflowDefinition verifyByDefineName(long projectCode, String workflowDefinitionName);

    List<WorkflowDefinition> queryAllDefinitionList(long projectCode);

    List<DependentSimplifyDefinition> queryDefinitionListByProjectCodeAndWorkflowDefinitionCodes(long projectCode,
                                                                                                 Collection<Long> codes);

    List<WorkflowDefinitionCountDto> countDefinitionByProjectCodes(Collection<Long> projectCodes);

    List<Long> queryDefinitionCodeListByProjectCodes(List<Long> projectCodes);

    List<ProjectWorkflowDefinitionCount> queryProjectWorkflowDefinitionCountByProjectCodes(List<Long> projectCodes);
}
