package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.yak.framework.security.common.entity.UserRole;
import io.yak.framework.security.common.po.UserRolePO;
import io.yak.framework.security.dao.UserRoleDao;
import io.yak.framework.security.dao.mapper.UserRoleMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.DatabaseNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/**
 * 用户角色关联数据访问实现。
 *
 * @author weifuwan
 */
@Repository
@RequiredArgsConstructor
public class UserRoleDaoImpl
        implements UserRoleDao {

    private final UserRoleMapper userRoleMapper;

    /**
     * 判断集合是否为空。
     */
    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    /**
     * 根据角色标识查询用户标识列表。
     *
     * @param roleId 角色标识
     * @return 用户标识列表
     */
    @Override
    public List<Long> selectUserIdListByRoleId(Long roleId) {
        if (roleId == null) {
            return java.util.Collections.emptyList();
        }

        return selectIdList(
                UserRolePO::getUserId,
                UserRolePO::getRoleId,
                roleId
        );
    }

    /**
     * 根据用户标识查询角色标识列表。
     *
     * @param userId 用户标识
     * @return 角色标识列表
     */
    @Override
    public List<Long> selectRoleIdListByUserId(Long userId) {
        if (userId == null) {
            return java.util.Collections.emptyList();
        }

        return selectIdList(
                UserRolePO::getRoleId,
                UserRolePO::getUserId,
                userId
        );
    }

    /**
     * 批量新增用户角色关联。
     *
     * <p>当前采用循环插入方式，适合单次关联数量较少的场景。</p>
     *
     * @param userRoleList 用户角色关联列表
     */
    @Override
    public void insertBatch(List<UserRole> userRoleList) {
        if (isEmpty(userRoleList)) {
            return;
        }

        CopyBeanUtil.copyList(
                userRoleList.stream()
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toList()),
                UserRolePO.class
        )
                .forEach(userRoleMapper::insert);
    }

    /**
     * 根据用户标识和角色标识删除关联。
     *
     * <p>两个参数同时存在时，删除同时满足两个条件的关联；
     * 仅传入一个参数时，按该参数删除关联。</p>
     *
     * @param userId 用户标识，可为空
     * @param roleId 角色标识，可为空
     * @return 删除的数据条数
     */
    @Override
    public int deleteByUserIdOrRoleId(
            Long userId,
            Long roleId) {

        if (userId == null && roleId == null) {
            return 0;
        }

        return userRoleMapper.delete(
                Wrappers.<UserRolePO>lambdaQuery()
                        .eq(
                                userId != null,
                                UserRolePO::getUserId,
                                userId
                        )
                        .eq(
                                roleId != null,
                                UserRolePO::getRoleId,
                                roleId
                        )
        );
    }

    /**
     * 统计指定角色关联的用户数量。
     *
     * @param roleId 角色标识
     * @return 用户角色关联数量
     */
    @Override
    public int selectCountByRoleId(Long roleId) {
        if (roleId == null) {
            return 0;
        }

        Long count = userRoleMapper.selectCount(
                Wrappers.<UserRolePO>lambdaQuery()
                        .eq(UserRolePO::getRoleId, roleId)
        );

        return Math.toIntExact(count);
    }

    /**
     * 根据角色标识列表查询用户角色关联。
     *
     * @param roleIds 角色标识列表
     * @return 用户角色关联列表
     */
    @Override
    public List<UserRolePO> selectByRoleIds(
            List<Long> roleIds) {

        if (isEmpty(roleIds)) {
            return java.util.Collections.emptyList();
        }

        return userRoleMapper.selectList(
                Wrappers.<UserRolePO>lambdaQuery()
                        .in(UserRolePO::getRoleId, roleIds)
        );
    }

    /**
     * 根据用户标识列表查询用户角色关联。
     *
     * @param userIds 用户标识列表
     * @return 用户角色关联列表
     */
    @Override
    public List<UserRolePO> getRoleIdListByUserIds(
            List<Long> userIds) {

        if (isEmpty(userIds)) {
            return java.util.Collections.emptyList();
        }

        return userRoleMapper.selectList(
                Wrappers.<UserRolePO>lambdaQuery()
                        .in(UserRolePO::getUserId, userIds)
        );
    }

    /**
     * 根据指定字段和值查询目标标识列表。
     */
    private List<Long> selectIdList(
            SFunction<UserRolePO, ?> selectedColumn,
            SFunction<UserRolePO, ?> conditionColumn,
            Long conditionValue) {

        return userRoleMapper.selectObjs(
                Wrappers.<UserRolePO>lambdaQuery()
                        .select(selectedColumn)
                        .eq(
                                conditionColumn,
                                conditionValue
                        )
        )
                .stream()
                .map(DatabaseNumberUtils::toLong)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }
}