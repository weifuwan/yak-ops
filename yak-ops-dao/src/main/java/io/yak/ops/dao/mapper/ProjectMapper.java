package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.ProjectUser;

import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * query project detail by code
     * @param projectCode projectCode
     * @return project
     */
    Project queryByCode(@Param("projectCode") long projectCode);

    /**
     * query project detail by code list
     * @param codes codes
     * @return project list
     */
    List<Project> queryByCodes(@Param("codes") Collection<Long> codes);

    /**
     * TODO: delete
     * query project detail by id
     * @param projectId projectId
     * @return project
     */
    Project queryDetailById(@Param("projectId") int projectId);

    /**
     * query project detail by code
     * @param projectCode projectCode
     * @return project
     */
    Project queryDetailByCode(@Param("projectCode") long projectCode);

    /**
     * query project by name
     * @param projectName projectName
     * @return project
     */
    Project queryByName(@Param("projectName") String projectName);

    /**
     * project page
     * @param page page
     * @param projectsIds projectsIds
     * @param searchName searchName
     * @return project Ipage
     */
    IPage<Project> queryProjectListPaging(IPage<Project> page,
                                          @Param("projectsIds") List<Integer> projectsIds,
                                          @Param("searchName") String searchName);

    /**
     *  query create project user
     * @param userId userId
     * @return project list
     */
    List<Project> queryProjectCreatedByUser(@Param("userId") int userId);

    /**
     * query authed project list by userId
     * @param userId userId
     * @return project list
     */
    List<Project> queryAuthedProjectListByUserId(@Param("userId") int userId);

    /**
     * query relation project list by userId
     * @param userId userId
     * @return project list
     */
    List<Project> queryRelationProjectListByUserId(@Param("userId") int userId);

    /**
     * query project except userId
     * @param userId userId
     * @return project list
     */
    List<Project> queryProjectExceptUserId(@Param("userId") int userId);

    /**
     * query project list by userId
     * @param userId
     * @return
     */
    List<Project> queryProjectCreatedAndAuthorizedByUserId(@Param("userId") int userId);

    /**
     * query project name and user name by workflowInstanceId.
     * @param workflowInstanceId workflowInstanceId
     * @return projectName and userName
     */
    ProjectUser queryProjectWithUserByWorkflowInstanceId(@Param("workflowInstanceId") int workflowInstanceId);

    /**
     * query all project
     * @param userId
     * @return projectList
     */
    List<Project> queryAllProject(@Param("userId") int userId);

    /**
     * list authorized Projects
     * @param userId
     * @param projectsIds
     * @param <T>
     * @return
     */
    List<Project> listAuthorizedProjects(@Param("userId") int userId, @Param("projectsIds") List<Integer> projectsIds);

    /**
     * query all project for dependent node
     * @return projectList
     */
    List<Project> queryAllProjectForDependent();

    /**
     * query the project by task instance id
     * @param taskInstanceId
     * @return project
     */
    Project queryProjectByTaskInstanceId(@Param("taskInstanceId") int taskInstanceId);

    /**
     * query all workflow count
     * @param projectsCodes projectsCodes
     * @return workflow count
     */
    int queryAllWorkflowCounts(@Param("projectsCodes") List<Long> projectsCodes);
}
