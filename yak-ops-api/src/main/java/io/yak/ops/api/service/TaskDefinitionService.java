package io.yak.ops.api.service;

import io.yak.ops.api.utils.Result;
import io.yak.ops.api.vo.TaskDefinitionVO;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.User;

import java.util.List;

public interface TaskDefinitionService {

    /**
     * query task definition
     *
     * @param loginUser login user
     * @param projectCode project code
     * @param workflowDefinitionCode workflow definition code
     * @param taskName task name
     * @return matching task definition
     */
    TaskDefinition queryTaskDefinitionByName(User loginUser,
                                             long projectCode,
                                             long workflowDefinitionCode,
                                             String taskName);

    /**
     * Get resource task definition by code
     *
     * @param loginUser login user
     * @param taskCode task code
     * @return TaskDefinition
     */
    TaskDefinition getTaskDefinition(User loginUser,
                                     long taskCode);

    /**
     * update task definition
     *
     * @param loginUser login user
     * @param projectCode project code
     * @param taskCode task code
     * @param version the version user want to switch
     */
    void switchVersion(User loginUser,
                       long projectCode,
                       long taskCode,
                       int version);

    /**
     * query the pagination versions info by one certain task definition code
     *
     * @param loginUser login user info to check auth
     * @param projectCode project code
     * @param taskCode task definition code
     * @param pageNo page number
     * @param pageSize page size
     * @return the pagination task definition versions info of the certain task definition
     */
    Result queryTaskDefinitionVersions(User loginUser,
                                       long projectCode,
                                       long taskCode,
                                       int pageNo,
                                       int pageSize);

    /**
     * delete the certain task definition version by version and code
     *
     * @param loginUser login user info
     * @param projectCode project code
     * @param taskCode the task definition code
     * @param version the task definition version user want to delete
     */
    void deleteByCodeAndVersion(User loginUser,
                                long projectCode,
                                long taskCode,
                                int version);

    /**
     * query detail of task definition by code
     *
     * @param loginUser login user
     * @param projectCode project code
     * @param taskCode the task definition code
     * @return task definition detail
     */
    TaskDefinitionVO queryTaskDefinitionDetail(User loginUser,
                                               long projectCode,
                                               long taskCode);

    /**
     * gen task code list
     *
     * @param genNum gen num
     * @return task code list
     */
    List<Long> genTaskCodeList(Integer genNum);

    /**
     * release task definition
     *
     * @param loginUser login user
     * @param projectCode project code
     * @param code task definition code
     * @param releaseState releaseState
     */
    void releaseTaskDefinition(User loginUser,
                               long projectCode,
                               long code,
                               ReleaseState releaseState);

    void deleteTaskByWorkflowDefinitionCode(long workflowDefinitionCode, int workflowDefinitionVersion);
}
