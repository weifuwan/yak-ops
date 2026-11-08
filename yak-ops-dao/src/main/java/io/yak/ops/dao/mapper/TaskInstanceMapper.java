package io.yak.ops.dao.mapper;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.dao.entity.ExecuteStatusCount;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.model.TaskInstanceStatusCountDto;
import io.yak.ops.dao.model.DataSyncExecutionDailyRow;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface TaskInstanceMapper extends BaseMapper<TaskInstance> {

    List<DataSyncExecutionDailyRow> queryDataSyncDailyExecution(@Param("projectCode") long projectCode,
                                                                @Param("startTime") Date startTime,
                                                                @Param("endTime") Date endTime);

    List<TaskInstance> queryLatestByTaskCodes(@Param("projectCode") long projectCode,
                                               @Param("taskType") String taskType,
                                               @Param("taskCodes") List<Long> taskCodes,
                                               @Param("states") int[] states);

    List<TaskInstance> findValidTaskListByWorkflowInstanceId(@Param("workflowInstanceId") Integer workflowInstanceId,
                                                             @Param("flag") Flag flag);

    TaskInstance queryByInstanceIdAndCode(@Param("workflowInstanceId") int workflowInstanceId,
                                          @Param("taskCode") Long taskCode);

    List<TaskInstance> queryByWorkflowInstanceIdsAndTaskCodes(@Param("workflowInstanceIds") List<Integer> workflowInstanceIds,
                                                              @Param("taskCodes") List<Long> taskCodes);

    /**
     * Statistics task instance group by given project codes list by start time
     * <p>
     * We only need project codes to determine whether the task instance belongs to the user or not.
     *
     * @param startTime    Statistics start time
     * @param endTime      Statistics end time
     * @param projectCodes Project codes list to filter
     * @return List of ExecuteStatusCount
     */
    List<TaskInstanceStatusCountDto> countTaskInstanceStateByProjectCodes(@Param("startTime") Date startTime,
                                                                          @Param("endTime") Date endTime,
                                                                          @Param("projectCodes") Collection<Long> projectCodes);

    /**
     * Statistics task instance group by given project ids list by start time
     * <p>
     * We only need project ids to determine whether the task instance belongs to the user or not.
     *
     * @param startTime    Statistics start time
     * @param endTime      Statistics end time
     * @param projectIds Project ids list to filter
     * @return List of ExecuteStatusCount
     */
    List<ExecuteStatusCount> countTaskInstanceStateByProjectIdsV2(@Param("startTime") Date startTime,
                                                                  @Param("endTime") Date endTime,
                                                                  @Param("projectIds") Set<Integer> projectIds);

    /**
     * Statistics task instance group by given project codes list by submit time
     * <p>
     * We only need project codes to determine whether the task instance belongs to the user or not.
     *
     * @param startTime    Statistics start time
     * @param endTime      Statistics end time
     * @param projectCode  projectCode
     * @param model model
     * @param projectIds projectIds
     * @return List of ExecuteStatusCount
     */
    List<ExecuteStatusCount> countTaskInstanceStateByProjectCodesAndStatesBySubmitTimeV2(@Param("startTime") Date startTime,
                                                                                         @Param("endTime") Date endTime,
                                                                                         @Param("projectCode") Long projectCode,
                                                                                         @Param("workflowCode") Long workflowCode,
                                                                                         @Param("taskCode") Long taskCode,
                                                                                         @Param("model") Integer model,
                                                                                         @Param("projectIds") Set<Integer> projectIds,
                                                                                         @Param("states") List<TaskExecutionStatus> states);

    IPage<TaskInstance> queryByProjectAndTaskType(
                                                  IPage<TaskInstance> page,
                                                  @Param("projectCode") long projectCode,
                                                  @Param("taskType") String taskType,
                                                  @Param("taskCode") Long taskCode,
                                                  @Param("states") int[] states);

    IPage<TaskInstance> queryTaskInstanceListPaging(IPage<TaskInstance> page,
                                                    @Param("projectCode") Long projectCode,
                                                    @Param("workflowInstanceId") Integer workflowInstanceId,
                                                    @Param("workflowInstanceName") String workflowInstanceName,
                                                    @Param("searchVal") String searchVal,
                                                    @Param("taskName") String taskName,
                                                    @Param("taskCode") Long taskCode,
                                                    @Param("executorName") String executorName,
                                                    @Param("states") int[] statusArray,
                                                    @Param("host") String host,
                                                    @Param("taskExecuteType") TaskExecuteType taskExecuteType,
                                                    @Param("startTime") Date startTime,
                                                    @Param("endTime") Date endTime);

    IPage<TaskInstance> queryStreamTaskInstanceListPaging(IPage<TaskInstance> page,
                                                          @Param("projectCode") Long projectCode,
                                                          @Param("workflowDefinitionName") String workflowDefinitionName,
                                                          @Param("searchVal") String searchVal,
                                                          @Param("taskName") String taskName,
                                                          @Param("taskCode") Long taskCode,
                                                          @Param("executorName") String executorName,
                                                          @Param("states") int[] statusArray,
                                                          @Param("host") String host,
                                                          @Param("taskExecuteType") TaskExecuteType taskExecuteType,
                                                          @Param("startTime") Date startTime,
                                                          @Param("endTime") Date endTime);

    void deleteByWorkflowInstanceId(@Param("workflowInstanceId") int workflowInstanceId);

    List<TaskInstance> findByWorkflowInstanceId(@Param("workflowInstanceId") Integer workflowInstanceId);

    /**
     * find last task instance list in the date interval
     *
     * @param taskCodes taskCodes
     * @return task instance list
     */
    List<TaskInstance> findLastTaskInstances(@Param("workflowInstanceId") Integer workflowInstanceId,
                                             @Param("taskCodes") Set<Long> taskCodes);

    TaskInstance findLastTaskInstance(@Param("workflowInstanceId") Integer workflowInstanceId,
                                      @Param("taskCode") long depTaskCode);

    void updateTaskInstanceState(@Param("taskInstanceId") Integer taskInstanceId,
                                 @Param("originState") int originState,
                                 @Param("targetState") int targetState);
}
