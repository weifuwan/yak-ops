package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.DependentSimplifyDefinition;
import io.yak.ops.dao.entity.ProjectWorkflowDefinitionCount;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.model.WorkflowDefinitionCountDto;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface WorkflowDefinitionMapper extends BaseMapper<WorkflowDefinition> {

    /**
     * query workflow definition by code
     *
     * @param code code
     * @return workflow definition
     */
    WorkflowDefinition queryByCode(@Param("code") long code);

    @Select("SELECT id FROM t_yak_ops_workflow_definition WHERE code = #{code} FOR UPDATE")
    Integer lockByCode(@Param("code") long code);

    /**
     * update
     */
    int updateById(@Param("et") WorkflowDefinition workflowDefinition);

    /**
     * delete workflow definition by code
     *
     * @param code code
     * @return delete result
     */
    int deleteByCode(@Param("code") long code);

    /**
     * query workflow definition by code list
     *
     * @param codes codes
     * @return workflow definition list
     */
    List<WorkflowDefinition> queryByCodes(@Param("codes") Collection<Long> codes);

    /**
     * verify workflow definition by workflowDefinitionName
     *
     * @param projectCode projectCode
     * @param workflowDefinitionName workflowDefinitionName
     * @return workflow definition
     */
    WorkflowDefinition verifyByDefineName(@Param("projectCode") long projectCode,
                                          @Param("workflowDefinitionName") String workflowDefinitionName);

    /**
     * query workflow definition by workflowDefinitionName
     *
     * @param projectCode projectCode
     * @param workflowDefinitionName workflowDefinitionName
     * @return workflow definition
     */
    WorkflowDefinition queryByDefineName(@Param("projectCode") long projectCode,
                                         @Param("workflowDefinitionName") String workflowDefinitionName);

    /**
     * query workflow definition by id
     *
     * @param workflowDefinitionId workflowDefinitionId
     * @return workflow definition
     */
    WorkflowDefinition queryByDefineId(@Param("workflowDefinitionId") int workflowDefinitionId);

    /**
     * workflow definition page
     *
     * @param page page
     * @param searchVal searchVal
     * @param userId userId
     * @param projectCode projectCode
     * @return workflow definition IPage
     */
    IPage<WorkflowDefinition> queryDefineListPaging(IPage<WorkflowDefinition> page,
                                                    @Param("searchVal") String searchVal,
                                                    @Param("userId") int userId,
                                                    @Param("projectCode") long projectCode);

    /**
     * Filter workflow definitions
     *
     * @param page page
     * @param workflowDefinition workflow definition object
     * @return workflow definition IPage
     */
    IPage<WorkflowDefinition> filterWorkflowDefinition(IPage<WorkflowDefinition> page,
                                                       @Param("pd") WorkflowDefinition workflowDefinition);

    /**
     * query all workflow definition list
     *
     * @param projectCode projectCode
     * @return workflow definition list
     */
    List<WorkflowDefinition> queryAllDefinitionList(@Param("projectCode") long projectCode);

    /**
     * query workflow definition list
     *
     * @param projectCode projectCode
     * @return workflow definition list
     */
    List<DependentSimplifyDefinition> queryDefinitionListByProjectCodeAndWorkflowDefinitionCodes(@Param("projectCode") long projectCode,
                                                                                                 @Param("codes") Collection<Long> codes);

    /**
     * query workflow definition by ids
     *
     * @param ids ids
     * @return workflow definition list
     */
    List<WorkflowDefinition> queryDefinitionListByIdList(@Param("ids") Integer[] ids);

    /**
     * Statistics workflow definition group by project codes list
     * <p>
     * We only need project codes to determine whether the definition belongs to the user or not.
     *
     * @param projectCodes projectCodes
     * @return definition group by user
     */
    List<WorkflowDefinitionCountDto> countDefinitionByProjectCodes(@Param("projectCodes") Collection<Long> projectCodes);

    /**
     * Statistics workflow definition group by project codes list
     * <p>
     * We only need project codes to determine whether the definition belongs to the user or not.
     *
     * @param projectCodes projectCodes
     * @param userId userId
     * @param releaseState releaseState
     * @return definition group by user
     */
    List<WorkflowDefinitionCountDto> countDefinitionByProjectCodesV2(@Param("projectCodes") List<Long> projectCodes,
                                                                     @Param("userId") Integer userId,
                                                                     @Param("releaseState") Integer releaseState);

    /**
     * list all project ids
     *
     * @return project ids list
     */
    List<Integer> listProjectIds();

    List<Long> queryDefinitionCodeListByProjectCodes(@Param("projectCodes") List<Long> projectCodes);

    List<ProjectWorkflowDefinitionCount> queryProjectWorkflowDefinitionCountByProjectCodes(@Param("projectCodes") List<Long> projectCodes);
}
