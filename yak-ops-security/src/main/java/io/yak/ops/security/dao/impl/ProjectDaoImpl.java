package io.yak.ops.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.security.common.dto.project.ProjectBriefQueryDTO;
import io.yak.ops.security.common.dto.project.ProjectQueryDTO;
import io.yak.ops.security.common.entity.project.Project;
import io.yak.ops.security.common.entity.project.ProjectBrief;
import io.yak.ops.security.common.po.ProjectPO;
import io.yak.ops.security.dao.ProjectDao;
import io.yak.ops.security.dao.mapper.ProjectMapper;
import io.yak.ops.security.util.CopyBeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 项目数据访问实现。
 *
 * @author weifuwan
 */
@Repository
@RequiredArgsConstructor
public class ProjectDaoImpl
        implements ProjectDao {

    private final ProjectMapper projectMapper;

    /**
     * 判断集合是否包含数据。
     */
    private static boolean hasItems(List<?> values) {
        return values != null && !values.isEmpty();
    }

    /**
     * 根据项目主键查询项目。
     *
     * @param projectId 项目主键
     * @return 项目信息；主键为空或项目不存在时返回 {@code null}
     */
    @Override
    public Project selectByProjectId(Long projectId) {
        if (projectId == null) {
            return null;
        }

        return CopyBeanUtil.copy(
                projectMapper.selectById(projectId),
                Project.class
        );
    }

    /**
     * 新增项目，并回填项目主键。
     *
     * @param project 项目信息
     */
    @Override
    public void insert(Project project) {
        ProjectPO projectPO =
                CopyBeanUtil.copy(project, ProjectPO.class);

        projectMapper.insert(projectPO);

        project.setId(projectPO.getId());
    }

    /**
     * 根据项目主键删除项目。
     *
     * @param projectId 项目主键
     */
    @Override
    public void deleteByProjectId(Long projectId) {
        if (projectId != null) {
            projectMapper.deleteById(projectId);
        }
    }

    /**
     * 根据主键更新项目。
     *
     * @param project 项目信息
     */
    @Override
    public void update(Project project) {
        projectMapper.updateById(
                CopyBeanUtil.copy(project, ProjectPO.class)
        );
    }

    /**
     * 查询同名项目数量，并排除指定项目。
     *
     * @param projectName 项目名称
     * @param projectId   需要排除的项目主键，可为空
     * @return 同名项目数量
     */
    @Override
    public int selectCountByProjectNameAndNotProjectId(
            String projectName,
            Long projectId) {

        Long count = projectMapper.selectCount(
                Wrappers.<ProjectPO>lambdaQuery()
                        .eq(
                                ProjectPO::getProjectName,
                                projectName
                        )
                        .ne(
                                projectId != null,
                                ProjectPO::getId,
                                projectId
                        )
        );

        return Math.toIntExact(count);
    }

    /**
     * 分页查询项目简要信息。
     *
     * @param queryDTO 查询条件
     * @return 项目简要信息分页数据
     */
    @Override
    public IPage<ProjectBrief> selectBriefPage(
            ProjectBriefQueryDTO queryDTO) {

        Page<ProjectPO> page = Page.of(
                queryDTO.getPage(),
                queryDTO.getSize()
        );

        LambdaQueryWrapper<ProjectPO> wrapper =
                briefQuery()
                        .like(
                                StringUtils.hasText(
                                        queryDTO.getProjectName()
                                ),
                                ProjectPO::getProjectName,
                                queryDTO.getProjectName()
                        )
                        .orderByDesc(ProjectPO::getId);

        IPage<ProjectPO> result =
                projectMapper.selectPage(page, wrapper);

        return CopyBeanUtil.copyPage(
                result,
                ProjectBrief.class
        );
    }

    /**
     * 查询全部项目简要信息。
     *
     * @return 项目简要信息列表
     */
    @Override
    public List<ProjectBrief> selectAllBriefList() {
        List<ProjectPO> projectPOList =
                projectMapper.selectList(
                        briefQuery()
                                .orderByAsc(
                                        ProjectPO::getProjectName
                                )
                );

        return CopyBeanUtil.copyList(
                projectPOList,
                ProjectBrief.class
        );
    }

    /**
     * 根据部门范围、项目范围及查询条件分页查询项目。
     *
     * @param queryDTO      查询条件
     * @param deptIdList    部门主键列表
     * @param projectIdList 项目主键列表
     * @return 项目分页数据
     */
    @Override
    public IPage<Project>
    selectPageByDeptIdListAndProjectIdList(
            ProjectQueryDTO queryDTO,
            List<Long> deptIdList,
            List<Long> projectIdList) {

        Page<ProjectPO> page = Page.of(
                queryDTO.getPage(),
                queryDTO.getSize()
        );

        LambdaQueryWrapper<ProjectPO> wrapper =
                Wrappers.<ProjectPO>lambdaQuery()
                        .eq(
                                queryDTO.getRunning() != null,
                                ProjectPO::getRunning,
                                queryDTO.getRunning()
                        )
                        .eq(
                                StringUtils.hasText(
                                        queryDTO.getProjectCode()
                                ),
                                ProjectPO::getProjectCode,
                                queryDTO.getProjectCode()
                        )
                        .like(
                                StringUtils.hasText(
                                        queryDTO.getProjectName()
                                ),
                                ProjectPO::getProjectName,
                                queryDTO.getProjectName()
                        )
                        .in(
                                hasItems(deptIdList),
                                ProjectPO::getDeptId,
                                deptIdList
                        )
                        .in(
                                hasItems(projectIdList),
                                ProjectPO::getId,
                                projectIdList
                        )
                        .orderByDesc(ProjectPO::getId);

        IPage<ProjectPO> result =
                projectMapper.selectPage(page, wrapper);

        return CopyBeanUtil.copyPage(
                result,
                Project.class
        );
    }

    /**
     * 根据项目主键集合查询项目简要信息。
     *
     * @param projectIds 项目主键集合
     * @return 项目列表
     */
    @Override
    public List<Project> selectProjectBriefByProjectIds(
            List<Long> projectIds) {

        if (!hasItems(projectIds)) {
            return java.util.Collections.emptyList();
        }

        List<ProjectPO> projectPOList =
                projectMapper.selectList(
                        briefQuery()
                                .in(
                                        ProjectPO::getId,
                                        projectIds
                                )
                );

        return CopyBeanUtil.copyList(
                projectPOList,
                Project.class
        );
    }

    /**
     * 创建项目简要信息查询条件。
     */
    private LambdaQueryWrapper<ProjectPO> briefQuery() {
        return Wrappers.<ProjectPO>lambdaQuery()
                .select(
                        ProjectPO::getId,
                        ProjectPO::getProjectCode,
                        ProjectPO::getProjectName
                );
    }
}