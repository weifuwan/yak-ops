package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.ProjectUser;
import io.yak.ops.dao.mapper.ProjectMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.ProjectDao;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.NonNull;

@Repository
public class ProjectDaoImpl extends BaseDao<Project, ProjectMapper> implements ProjectDao {

    public ProjectDaoImpl(@NonNull ProjectMapper projectMapper) {
        super(projectMapper);
    }

    @Override
    public List<Project> queryByCodes(Collection<Long> projectCodes) {
        return mybatisMapper.queryByCodes(projectCodes);
    }

    @Override
    public Project queryByCode(Long projectCode) {
        return mybatisMapper.queryByCode(projectCode);
    }

    @Override
    public Project queryDetailById(int projectId) {
        return mybatisMapper.queryDetailById(projectId);
    }

    @Override
    public Project queryByName(String projectName) {
        return mybatisMapper.queryByName(projectName);
    }

    @Override
    public IPage<Project> queryProjectListPaging(IPage<Project> page,
                                                 List<Integer> projectsIds,
                                                 String searchName) {
        return mybatisMapper.queryProjectListPaging(page, projectsIds, searchName);
    }

    @Override
    public List<Project> queryProjectCreatedByUser(int userId) {
        return mybatisMapper.queryProjectCreatedByUser(userId);
    }

    @Override
    public List<Project> queryAuthedProjectListByUserId(int userId) {
        return mybatisMapper.queryAuthedProjectListByUserId(userId);
    }

    @Override
    public List<Project> queryProjectCreatedAndAuthorizedByUserId(int userId) {
        return mybatisMapper.queryProjectCreatedAndAuthorizedByUserId(userId);
    }

    @Override
    public ProjectUser queryProjectWithUserByWorkflowInstanceId(int workflowInstanceId) {
        return mybatisMapper.queryProjectWithUserByWorkflowInstanceId(workflowInstanceId);
    }

    @Override
    public List<Project> queryAllProject(int userId) {
        return mybatisMapper.queryAllProject(userId);
    }

    @Override
    public List<Project> listAuthorizedProjects(int userId, List<Integer> projectsIds) {
        return mybatisMapper.listAuthorizedProjects(userId, projectsIds);
    }

    @Override
    public List<Project> queryAllProjectForDependent() {
        return mybatisMapper.queryAllProjectForDependent();
    }

    @Override
    public Project queryProjectByTaskInstanceId(int taskInstanceId) {
        return mybatisMapper.queryProjectByTaskInstanceId(taskInstanceId);
    }
}
