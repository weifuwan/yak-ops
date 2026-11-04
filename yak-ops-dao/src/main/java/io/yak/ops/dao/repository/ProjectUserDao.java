package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.ProjectUser;

public interface ProjectUserDao extends IDao<ProjectUser> {

    ProjectUser queryProjectRelation(int projectId, int userId);

    void deleteProjectRelation(int projectId, int userId);
}
