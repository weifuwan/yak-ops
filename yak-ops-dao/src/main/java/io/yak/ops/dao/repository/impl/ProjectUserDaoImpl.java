package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.ProjectUser;
import io.yak.ops.dao.mapper.ProjectUserMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.ProjectUserDao;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class ProjectUserDaoImpl extends BaseDao<ProjectUser, ProjectUserMapper> implements ProjectUserDao {

    public ProjectUserDaoImpl(@NonNull ProjectUserMapper projectUserMapper) {
        super(projectUserMapper);
    }

    @Override
    public ProjectUser queryProjectRelation(int projectId, int userId) {
        return mybatisMapper.queryProjectRelation(projectId, userId);
    }

    @Override
    public void deleteProjectRelation(int projectId, int userId) {
        mybatisMapper.deleteProjectRelation(projectId, userId);
    }
}
