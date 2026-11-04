package io.yak.ops.dao.mapper;

import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.ProjectUser;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProjectUserMapperTest extends BaseDaoTest {

    @Autowired
    private ProjectUserMapper projectUserMapper;

    /**
     * insert
     *
     * @return ProjectUser
     */
    private ProjectUser insertOne() {
        // insertOne
        ProjectUser projectUser = new ProjectUser();
        projectUser.setProjectId(1010);
        projectUser.setUserId(111);
        projectUserMapper.insert(projectUser);
        return projectUser;
    }

    /**
     * test update
     */
    @Test
    public void testUpdate() {
        // insertOne
        ProjectUser projectUser = insertOne();
        projectUser.setCreateTime(new Date());
        // update
        int update = projectUserMapper.updateById(projectUser);
        Assertions.assertEquals(update, 1);
    }

    /**
     * test delete
     */
    @Test
    public void testDelete() {
        ProjectUser projectUserMap = insertOne();
        int delete = projectUserMapper.deleteById(projectUserMap.getId());
        Assertions.assertEquals(delete, 1);
    }

    /**
     * test query
     */
    @Test
    public void testQuery() {
        ProjectUser projectUser = insertOne();
        // query
        List<ProjectUser> projectUsers = projectUserMapper.selectList(null);
        Assertions.assertNotEquals(0, projectUsers.size());
    }

    /**
     * test delete project relation
     */
    @Test
    public void testDeleteProjectRelation() {

        ProjectUser projectUser = insertOne();
        int delete = projectUserMapper.deleteProjectRelation(projectUser.getProjectId(), projectUser.getUserId());
        Assertions.assertTrue(delete >= 1);

    }

    /**
     * test query project relation
     */
    @Test
    public void testQueryProjectRelation() {
        ProjectUser projectUser = insertOne();
        ProjectUser projectUser1 =
                projectUserMapper.queryProjectRelation(projectUser.getProjectId(), projectUser.getUserId());
        Assertions.assertNotEquals(null, projectUser1);

    }
}
