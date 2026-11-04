package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.WorkflowDefinitionLog;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface WorkflowDefinitionLogMapper extends BaseMapper<WorkflowDefinitionLog> {

    /**
     * query the certain workflow definition version info by workflow definition code and version number
     *
     * @param code workflow definition code
     * @param version version number
     * @return the workflow definition version info
     */

    WorkflowDefinitionLog queryByDefinitionCodeAndVersion(@Param("code") long code, @Param("version") int version);

    /**
     * query workflow definition log by name
     *
     * @param projectCode projectCode
     * @param name workflow definition name
     * @return workflow definition log list
     */
    List<WorkflowDefinitionLog> queryByDefinitionName(@Param("projectCode") long projectCode,
                                                      @Param("name") String name);

    /**
     * query workflow definition log list
     *
     * @param code workflow definition code
     * @return workflow definition log list
     */
    List<WorkflowDefinitionLog> queryByDefinitionCode(@Param("code") long code);

    /**
     * query max version for definition
     */
    Integer queryMaxVersionForDefinition(@Param("code") long code);

    /**
     * query max version definition log
     */
    WorkflowDefinitionLog queryMaxVersionDefinitionLog(@Param("code") long code);

    /**
     * query the paging workflow definition version list by pagination info
     *
     * @param page pagination info
     * @param code workflow definition code
     * @param projectCode project code
     * @return the paging workflow definition version list
     */
    IPage<WorkflowDefinitionLog> queryWorkflowDefinitionVersionsPaging(Page<WorkflowDefinitionLog> page,
                                                                       @Param("code") long code,
                                                                       @Param("projectCode") long projectCode);

    /**
     * delete the certain workflow definition version by workflow definition id and version number
     *
     * @param code workflow definition code
     * @param version version number
     * @return delete result
     */
    int deleteByWorkflowDefinitionCodeAndVersion(@Param("code") long code, @Param("version") int version);

    void deleteByWorkflowDefinitionCode(@Param("workflowDefinitionCode") long workflowDefinitionCode);
}
