package io.yak.ops.dao.mapper;

import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.ProjectWorkerGroup;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

public class ProjectWorkerGroupMapperTest extends BaseDaoTest {

    @Autowired
    private ProjectWorkerGroupMapper projectWorkerGroupMapper;

    /**
     * insert
     *
     * @return ProjectWorkerGroup
     */
    private ProjectWorkerGroup insertOne() {
        // insertOne
        ProjectWorkerGroup projectWorkerGroup = new ProjectWorkerGroup();

        projectWorkerGroup.setProjectCode(1L);
        projectWorkerGroup.setWorkerGroup("WorkerGroup1");;
        projectWorkerGroupMapper.insert(projectWorkerGroup);
        return projectWorkerGroup;
    }

    /**
     * test update
     */
    @Test
    public void testUpdate() {
        // insertOne
        ProjectWorkerGroup projectWorkerGroup = insertOne();
        projectWorkerGroup.setCreateTime(new Date());
        // update
        int update = projectWorkerGroupMapper.updateById(projectWorkerGroup);
        Assertions.assertEquals(update, 1);
    }

    /**
     * test delete
     */
    @Test
    public void testDelete() {
        ProjectWorkerGroup projectWorkerGroup = insertOne();
        int delete = projectWorkerGroupMapper.deleteById(projectWorkerGroup.getId());
        Assertions.assertEquals(delete, 1);
    }

    /**
     * test query
     */
    @Test
    public void testQuery() {
        ProjectWorkerGroup projectWorkerGroup = insertOne();
        // query
        List<ProjectWorkerGroup> projectUsers = projectWorkerGroupMapper.selectList(null);
        Assertions.assertNotEquals(0, projectUsers.size());
    }

    /**
     * test delete the relation of project and worker group
     */
    @Test
    public void testDeleteProjectWorkerGroupRelation() {

        ProjectWorkerGroup projectWorkerGroup = insertOne();
        int delete = projectWorkerGroupMapper.delete(new QueryWrapper<ProjectWorkerGroup>()
                .lambda()
                .eq(ProjectWorkerGroup::getProjectCode, projectWorkerGroup.getProjectCode())
                .eq(ProjectWorkerGroup::getWorkerGroup, projectWorkerGroup.getWorkerGroup()));

        Assertions.assertTrue(delete >= 1);
    }

    /**
     * test query the relation of project and worker group
     */
    @Test
    public void testQueryProjectWorkerGroupRelation() {
        ProjectWorkerGroup projectWorkerGroup = insertOne();
        projectWorkerGroup = projectWorkerGroupMapper.selectOne(new QueryWrapper<ProjectWorkerGroup>()
                .lambda()
                .eq(ProjectWorkerGroup::getProjectCode, projectWorkerGroup.getProjectCode())
                .eq(ProjectWorkerGroup::getWorkerGroup, projectWorkerGroup.getWorkerGroup()));

        Assertions.assertNotEquals(null, projectWorkerGroup);
    }
}
