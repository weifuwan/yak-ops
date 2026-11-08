package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskDefinitionLog;
import io.yak.ops.dao.entity.TaskMainInfo;
import io.yak.ops.dao.model.DataSyncOverviewTaskRow;
import io.yak.ops.dao.model.DataSyncTaskPageRow;
import io.yak.ops.dao.model.WorkflowDefinitionCountDto;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface TaskDefinitionMapper extends BaseMapper<TaskDefinition> {

    /**
     * query task definition by name
     *
     * @param projectCode projectCode
     * @param workflowDefinitionCode workflowDefinitionCode
     * @param name name
     * @return task definition
     */
    TaskDefinition queryByName(@Param("projectCode") long projectCode,
                               @Param("workflowDefinitionCode") long workflowDefinitionCode,
                               @Param("name") String name);

    /**
     * query task definition by code
     *
     * @param code taskDefinitionCode
     * @return task definition
     */
    TaskDefinition queryByCode(@Param("code") long code);

    @Update("UPDATE t_yak_ops_task_definition SET version = #{nextRevision} "
            + "WHERE code = #{taskCode} AND project_code = #{projectCode} "
            + "AND task_type = 'DATA_SYNC' AND flag = 1 AND version = #{expectedRevision}")
    int updateDataSyncRevision(@Param("projectCode") long projectCode,
                               @Param("taskCode") long taskCode,
                               @Param("expectedRevision") int expectedRevision,
                               @Param("nextRevision") int nextRevision);

    IPage<DataSyncTaskPageRow> queryDataSyncPage(IPage<DataSyncTaskPageRow> page,
                                            @Param("projectCode") long projectCode,
                                            @Param("taskType") String taskType,
                                            @Param("keyword") String keyword,
                                            @Param("taskCode") Long taskCode,
                                            @Param("releaseState") Integer releaseState,
                                            @Param("taskCodes") List<Long> taskCodes);

    List<DataSyncOverviewTaskRow> queryDataSyncOverview(@Param("projectCode") long projectCode);

    /**
     * query all task definition list
     *
     * @param projectCode projectCode
     * @return task definition list
     */
    List<String> queryAllTaskDefinitionWorkerGroups(@Param("projectCode") long projectCode);

    /**
     * count task definition group by user
     *
     * @param projectCodes projectCodes
     * @return task definition list
     */
    List<WorkflowDefinitionCountDto> countDefinitionGroupByUser(@Param("projectCodes") Long[] projectCodes);

    /**
     * delete task definition by code
     *
     * @param code code
     * @return int
     */
    int deleteByCode(@Param("code") long code);

    /**
     * batch insert task definitions
     *
     * @param taskDefinitions taskDefinitions
     * @return int
     */
    int batchInsert(@Param("taskDefinitions") List<TaskDefinitionLog> taskDefinitions);

    /**
     * task main info
     * @param projectCode project code
     * @param codeList code list
     * @return task main info
     */
    List<TaskMainInfo> queryDefineListByCodeList(@Param("projectCode") long projectCode,
                                                 @Param("codeList") List<Long> codeList);

    /**
     * query task definition by code list
     *
     * @param codes taskDefinitionCode list
     * @return task definition list
     */
    List<TaskDefinition> queryByCodeList(@Param("codes") Collection<Long> codes);

    /**
     * Filter task definition
     *
     * @param page page
     * @param taskDefinition task definition
     * @return task definition IPage
     */
    IPage<TaskDefinition> filterTaskDefinition(IPage<TaskDefinition> page,
                                               @Param("task") TaskDefinition taskDefinition);

    /**
     * batch delete task by task code
     *
     * @param taskCodeList task code list
     * @return deleted row count
     */
    int deleteByBatchCodes(@Param("taskCodeList") List<Long> taskCodeList);

    void deleteByWorkflowDefinitionCodeAndVersion(long workflowDefinitionCode, int workflowDefinitionVersion);

    List<TaskDefinition> queryDefinitionsByTaskType(@Param("taskType") String taskType);
}
