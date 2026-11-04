package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.ProjectUser;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface ProjectDao extends IDao<Project> {

    List<Project> queryByCodes(Collection<Long> projectCodes);

    Project queryByCode(Long projectCode);

    Project queryDetailById(int projectId);

    Project queryByName(String projectName);

    IPage<Project> queryProjectListPaging(IPage<Project> page,
                                          List<Integer> projectsIds,
                                          String searchName);

    List<Project> queryProjectCreatedByUser(int userId);

    List<Project> queryAuthedProjectListByUserId(int userId);

    List<Project> queryProjectCreatedAndAuthorizedByUserId(int userId);

    ProjectUser queryProjectWithUserByWorkflowInstanceId(int workflowInstanceId);

    List<Project> queryAllProject(int userId);

    List<Project> listAuthorizedProjects(int userId, List<Integer> projectsIds);

    List<Project> queryAllProjectForDependent();

    Project queryProjectByTaskInstanceId(int taskInstanceId);
}
