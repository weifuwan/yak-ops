package io.yak.ops.security.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.security.common.dto.project.ProjectBriefQueryDTO;
import io.yak.ops.security.common.dto.project.ProjectQueryDTO;
import io.yak.ops.security.common.entity.project.Project;
import io.yak.ops.security.common.entity.project.ProjectBrief;

import java.util.List;

/**
 * 项目数据访问接口。
 */
public interface ProjectDao {
    Project selectByProjectId(Long projectId);

    void insert(Project var1);

    IPage<Project> selectPageByDeptIdListAndProjectIdList(
            ProjectQueryDTO var1, List<Long> var2, List<Long> var3);

    void deleteByProjectId(Long projectId);

    int selectCountByProjectNameAndNotProjectId(String projectName, Long projectId);

    IPage<ProjectBrief> selectBriefPage(ProjectBriefQueryDTO var1);

    List<ProjectBrief> selectAllBriefList();

    void update(Project var1);

    List<Project> selectProjectBriefByProjectIds(List<Long> var1);
}
