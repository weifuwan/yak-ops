package io.yak.ops.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.security.common.dto.user.UserProjectDTO;
import io.yak.ops.security.common.entity.UserProject;
import io.yak.ops.security.common.po.UserProjectPO;
import io.yak.ops.security.dao.UserProjectDao;
import io.yak.ops.security.dao.mapper.UserProjectMapper;
import io.yak.ops.security.util.CopyBeanUtil;
import io.yak.ops.security.util.DatabaseNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户项目关联数据访问实现。
 *
 * @author weifuwan
 */
@Repository
@RequiredArgsConstructor
public class UserProjectDaoImpl
        implements UserProjectDao {

    private final UserProjectMapper userProjectMapper;

    /**
     * 判断集合是否为空。
     */
    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    /**
     * 根据项目和用户类型查询用户标识。
     *
     * @param projectId 项目标识
     * @param userType  项目中的用户类型
     * @return 用户标识列表
     */
    @Override
    public List<Long> selectUserIdListByProjectId(
            Long projectId,
            int userType) {

        if (projectId == null) {
            return java.util.Collections.emptyList();
        }

        return userProjectMapper.selectObjs(
                Wrappers.<UserProjectPO>lambdaQuery()
                        .select(UserProjectPO::getUserId)
                        .eq(
                                UserProjectPO::getProjectId,
                                projectId
                        )
                        .eq(
                                UserProjectPO::getUserType,
                                userType
                        )
        )
                .stream()
                .map(DatabaseNumberUtils::toLong)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 根据项目标识列表查询用户项目关联。
     *
     * @param projectIds 项目标识列表
     * @return 用户项目关联列表
     */
    @Override
    public List<UserProject> selectByProjectIds(
            List<Long> projectIds) {

        if (isEmpty(projectIds)) {
            return java.util.Collections.emptyList();
        }

        List<UserProjectPO> records =
                userProjectMapper.selectList(
                        briefQuery()
                                .in(
                                        UserProjectPO::getProjectId,
                                        projectIds
                                )
                );

        return CopyBeanUtil.copyList(
                records,
                UserProject.class
        );
    }

    /**
     * 根据用户标识列表查询项目标识。
     *
     * @param userIdList 用户标识列表
     * @return 项目标识列表
     */
    @Override
    public List<Long> selectProjectIdListByUserIdList(
            List<Long> userIdList) {

        if (isEmpty(userIdList)) {
            return java.util.Collections.emptyList();
        }

        return userProjectMapper.selectObjs(
                Wrappers.<UserProjectPO>lambdaQuery()
                        .select(
                                UserProjectPO::getProjectId
                        )
                        .in(
                                UserProjectPO::getUserId,
                                userIdList
                        )
        )
                .stream()
                .map(DatabaseNumberUtils::toLong)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 根据用户标识列表查询用户项目持久化数据。
     *
     * @param userIdList 用户标识列表
     * @return 用户项目关联列表
     */
    @Override
    public List<UserProjectPO> selectProjectListByUserIdList(
            List<Long> userIdList) {

        if (isEmpty(userIdList)) {
            return java.util.Collections.emptyList();
        }

        return userProjectMapper.selectList(
                Wrappers.<UserProjectPO>lambdaQuery()
                        .in(
                                UserProjectPO::getUserId,
                                userIdList
                        )
        );
    }

    /**
     * 批量保存用户项目关联。
     *
     * <p>已存在的关联执行更新，不存在的关联执行新增。</p>
     *
     * @param userProjectList 用户项目关联列表
     */
    @Override
    public void insertBatch(
            List<UserProject> userProjectList) {

        if (isEmpty(userProjectList)) {
            return;
        }

        for (UserProject userProject : userProjectList) {
            if (userProject == null) {
                continue;
            }

            UserProjectPO existing =
                    selectExisting(userProject);

            if (existing == null) {
                insertUserProject(userProject);
            } else {
                updateUserProject(
                        existing.getId(),
                        userProject
                );
            }
        }
    }

    /**
     * 删除指定的用户项目关联。
     *
     * @param userProjectList 待删除的关联列表
     * @return 删除的数据条数
     */
    @Override
    public int deleteUserProject(
            List<UserProject> userProjectList) {

        if (isEmpty(userProjectList)) {
            return 0;
        }

        int deletedCount = 0;

        for (UserProject userProject : userProjectList) {
            if (userProject == null) {
                continue;
            }

            deletedCount += userProjectMapper.delete(
                    Wrappers.<UserProjectPO>lambdaQuery()
                            .eq(
                                    UserProjectPO::getProjectId,
                                    userProject.getProjectId()
                            )
                            .eq(
                                    UserProjectPO::getUserId,
                                    userProject.getUserId()
                            )
                            .eq(
                                    userProject.getUserType() != null,
                                    UserProjectPO::getUserType,
                                    userProject.getUserType()
                            )
            );
        }

        return deletedCount;
    }

    /**
     * 删除指定项目的全部用户关联。
     *
     * @param projectId 项目标识
     */
    @Override
    public void deleteByProjectId(Long projectId) {
        if (projectId == null) {
            return;
        }

        userProjectMapper.delete(
                Wrappers.<UserProjectPO>lambdaQuery()
                        .eq(
                                UserProjectPO::getProjectId,
                                projectId
                        )
        );
    }

    /**
     * 删除指定用户的全部项目关联。
     */
    @Override
    public void deleteByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        userProjectMapper.delete(
                Wrappers.<UserProjectPO>lambdaQuery()
                        .eq(UserProjectPO::getUserId, userId)
        );
    }

    /**
     * 删除指定项目和用户类型的关联。
     *
     * @param projectId 项目标识
     * @param userType  项目中的用户类型
     */
    @Override
    public void deleteByProjectIdAndUserType(
            Long projectId,
            int userType) {

        if (projectId == null) {
            return;
        }

        userProjectMapper.delete(
                Wrappers.<UserProjectPO>lambdaQuery()
                        .eq(
                                UserProjectPO::getProjectId,
                                projectId
                        )
                        .eq(
                                UserProjectPO::getUserType,
                                userType
                        )
        );
    }

    /**
     * 根据查询条件查询用户项目关联。
     *
     * @param queryDTO 查询条件，可为空
     * @return 用户项目关联列表
     */
    @Override
    public List<UserProject> select(
            UserProjectDTO queryDTO) {

        LambdaQueryWrapper<UserProjectPO> wrapper =
                Wrappers.<UserProjectPO>lambdaQuery()
                        .select(
                                UserProjectPO::getId,
                                UserProjectPO::getProjectId,
                                UserProjectPO::getUserId,
                                UserProjectPO::getUserType
                        );

        if (queryDTO != null) {
            wrapper
                    .eq(
                            queryDTO.getId() != null,
                            UserProjectPO::getId,
                            queryDTO.getId()
                    )
                    .eq(
                            queryDTO.getProjectId() != null,
                            UserProjectPO::getProjectId,
                            queryDTO.getProjectId()
                    )
                    .eq(
                            queryDTO.getUserId() != null,
                            UserProjectPO::getUserId,
                            queryDTO.getUserId()
                    )
                    .eq(
                            queryDTO.getUserType() != null,
                            UserProjectPO::getUserType,
                            queryDTO.getUserType()
                    )
                    .eq(
                            queryDTO.getIsDelete() != null,
                            UserProjectPO::getIsDelete,
                            queryDTO.getIsDelete()
                    );
        }

        return CopyBeanUtil.copyList(
                userProjectMapper.selectList(wrapper),
                UserProject.class
        );
    }

    /**
     * 查询已经存在的用户项目关联。
     */
    private UserProjectPO selectExisting(
            UserProject userProject) {

        return userProjectMapper.selectOne(
                Wrappers.<UserProjectPO>lambdaQuery()
                        .eq(
                                UserProjectPO::getProjectId,
                                userProject.getProjectId()
                        )
                        .eq(
                                UserProjectPO::getUserId,
                                userProject.getUserId()
                        )
                        .eq(
                                userProject.getUserType() != null,
                                UserProjectPO::getUserType,
                                userProject.getUserType()
                        )
        );
    }

    /**
     * 新增用户项目关联。
     */
    private int insertUserProject(
            UserProject userProject) {

        return userProjectMapper.insert(
                CopyBeanUtil.copy(
                        userProject,
                        UserProjectPO.class
                )
        );
    }

    /**
     * 根据主键更新用户项目关联。
     */
    private int updateUserProject(
            Long id,
            UserProject userProject) {

        UserProjectPO userProjectPO =
                CopyBeanUtil.copy(
                        userProject,
                        UserProjectPO.class
                );

        userProjectPO.setId(id);

        return userProjectMapper.updateById(userProjectPO);
    }

    /**
     * 创建用户项目简要查询条件。
     */
    private LambdaQueryWrapper<UserProjectPO> briefQuery() {
        return Wrappers.<UserProjectPO>lambdaQuery()
                .select(
                        UserProjectPO::getUserId,
                        UserProjectPO::getProjectId,
                        UserProjectPO::getUserType
                );
    }
}
