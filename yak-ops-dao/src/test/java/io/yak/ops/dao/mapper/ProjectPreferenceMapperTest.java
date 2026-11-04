package io.yak.ops.dao.mapper;

import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.ProjectPreference;

import java.util.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

public class ProjectPreferenceMapperTest extends BaseDaoTest {

    @Autowired
    private ProjectPreferenceMapper projectPreferenceMapper;

    private ProjectPreference insertOne(long code, long projectCode) {
        ProjectPreference projectPreference = new ProjectPreference();
        projectPreference.setCode(code);
        projectPreference.setProjectCode(projectCode);
        projectPreference.setPreferences("{workerGroup:{availableOptions:[],default:1}}");
        projectPreference.setCreateTime(new Date());
        projectPreference.setUpdateTime(new Date());
        projectPreference.setUserId(1);
        projectPreferenceMapper.insert(projectPreference);
        return projectPreference;
    }

    @Test
    public void testUpdate() {
        ProjectPreference projectPreference = insertOne(1, 1);
        projectPreference.setUpdateTime(new Date());

        int update = projectPreferenceMapper.updateById(projectPreference);
        Assertions.assertEquals(1, update);
    }

    @Test
    public void testQueryByProjectCode() {
        long projectCode = 2;
        ProjectPreference expectedProjectPreference = insertOne(2, projectCode);

        ProjectPreference projectPreference = projectPreferenceMapper
                .selectOne(new QueryWrapper<ProjectPreference>().lambda().eq(ProjectPreference::getProjectCode,
                        projectCode));
        Assertions.assertEquals(expectedProjectPreference, projectPreference);
    }

}
