package io.yak.ops.api.service;

import io.yak.ops.api.utils.Result;
import io.yak.ops.api.vo.ScheduleVO;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.dao.entity.Schedule;
import io.yak.ops.dao.entity.User;

import java.util.List;

public interface SchedulerService {

    /**
     * save schedule
     *
     * @param loginUser login user
     * @param projectCode project code
     * @param workflowDefinitionCode workflow definition code
     * @param schedule scheduler
     * @param warningType warning type
     * @param warningGroupId warning group id
     * @param failureStrategy failure strategy
     * @param workflowInstancePriority workflow instance priority
     * @param workerGroup worker group
     * @param tenantCode tenant code
     * @param environmentCode environment code
     */
    Schedule insertSchedule(User loginUser,
                            long projectCode,
                            long workflowDefinitionCode,
                            String schedule,
                            WarningType warningType,
                            int warningGroupId,
                            FailureStrategy failureStrategy,
                            Priority workflowInstancePriority,
                            String workerGroup,
                            String tenantCode,
                            Long environmentCode);

    /**
     * updateWorkflowInstance schedule
     *
     * @param loginUser login user
     * @param projectCode project code
     * @param id scheduler id
     * @param scheduleExpression scheduler
     * @param warningType warning type
     * @param warningGroupId warning group id
     * @param failureStrategy failure strategy
     * @param workerGroup worker group
     * @param tenantCode tenant code
     * @param environmentCode environment code
     * @param workflowInstancePriority workflow instance priority
     */
    Schedule updateSchedule(User loginUser,
                            long projectCode,
                            Integer id,
                            String scheduleExpression,
                            WarningType warningType,
                            int warningGroupId,
                            FailureStrategy failureStrategy,
                            Priority workflowInstancePriority,
                            String workerGroup,
                            String tenantCode,
                            Long environmentCode);

    /**
     * query schedule
     *
     * @param loginUser login user
     * @param projectCode project code
     * @param workflowDefinitionCode workflow definition code
     * @param pageNo page number
     * @param pageSize page size
     * @param searchVal search value
     * @return schedule list page
     */
    Result querySchedule(User loginUser, long projectCode, long workflowDefinitionCode, String searchVal,
                         Integer pageNo, Integer pageSize);

    List<Schedule> queryScheduleByWorkflowDefinitionCodes(List<Long> workflowDefinitionCodes);

    /**
     * query schedule list
     *
     * @param loginUser   login user
     * @param projectCode project code
     * @return schedule list
     */
    List<ScheduleVO> queryScheduleList(User loginUser, long projectCode);

    /**
     * delete schedule by id
     *
     * @param loginUser login user
     * @param scheduleId schedule id
     */
    void deleteSchedulesById(User loginUser, Integer scheduleId);

    /**
     * preview schedule
     *
     * @param loginUser login user
     * @param schedule schedule expression
     * @return the next five fire time
     */
    List<String> previewSchedule(User loginUser, String schedule);

    /**
     * update workflow definition schedule
     *
     * @param loginUser login user
     * @param projectCode project code
     * @param workflowDefinitionCode workflow definition code
     * @param scheduleExpression scheduleExpression
     * @param warningType warning type
     * @param warningGroupId warning group id
     * @param failureStrategy failure strategy
     * @param workerGroup worker group
     * @param tenantCode tenant code
     * @param workflowInstancePriority workflow instance priority
     */
    Schedule updateScheduleByWorkflowDefinitionCode(User loginUser,
                                                    long projectCode,
                                                    long workflowDefinitionCode,
                                                    String scheduleExpression,
                                                    WarningType warningType,
                                                    int warningGroupId,
                                                    FailureStrategy failureStrategy,
                                                    Priority workflowInstancePriority,
                                                    String workerGroup,
                                                    String tenantCode,
                                                    long environmentCode);

    /**
     * Online the scheduler by scheduler id, if the related workflow definition is not online will throw exception.
     */
    void onlineScheduler(User loginUser, Long projectCode, Integer schedulerId);

    /**
     * Do online scheduler by workflow code, this method will not do permission check.
     */
    void onlineSchedulerByWorkflowCode(Long workflowDefinitionCode);

    /**
     * Offline the scheduler by scheduler id, will not offline the related workflow definition.
     */
    void offlineScheduler(User loginUser, Long projectCode, Integer schedulerId);

    /**
     * Do offline scheduler by workflow code, this method will not do permission check.
     */
    void offlineSchedulerByWorkflowCode(Long workflowDefinitionCode);
}
