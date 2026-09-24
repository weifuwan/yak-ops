package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.framework.security.common.entity.RolePermission;
import io.yak.framework.security.common.po.RolePermissionPO;
import io.yak.framework.security.dao.RolePermissionDao;
import io.yak.framework.security.dao.mapper.RolePermissionMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.DatabaseNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色权限关联数据访问实现。
 *
 * @author weifuwan
 */
@Repository
@RequiredArgsConstructor
public class RolePermissionDaoImpl
        implements RolePermissionDao {

    private final RolePermissionMapper rolePermissionMapper;

    /**
     * 批量新增角色权限关联。
     *
     * <p>当前采用循环插入方式，适用于角色权限数量较少的场景。</p>
     *
     * @param items 角色权限关联列表
     */
    @Override
    public void insertBatch(List<RolePermission> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        CopyBeanUtil.copyList(
                items,
                RolePermissionPO.class
        )
                .forEach(rolePermissionMapper::insert);
    }

    /**
     * 删除指定角色的全部权限关联。
     *
     * @param roleId 角色标识
     */
    @Override
    public void deleteByRoleId(Long roleId) {
        if (roleId == null) {
            return;
        }

        rolePermissionMapper.delete(
                Wrappers.<RolePermissionPO>lambdaQuery()
                        .eq(RolePermissionPO::getRoleId, roleId)
        );
    }

    /**
     * 删除指定权限的全部角色关联。
     */
    @Override
    public void deleteByPermissionId(Long permissionId) {
        if (permissionId == null) {
            return;
        }
        rolePermissionMapper.delete(
                Wrappers.<RolePermissionPO>lambdaQuery()
                        .eq(RolePermissionPO::getPermissionId, permissionId)
        );
    }

    /**
     * 查询指定角色关联的权限标识。
     *
     * @param roleId 角色标识
     * @return 权限标识列表
     */
    @Override
    public List<Long> selectPermissionIdListByRoleId(
            Long roleId) {

        if (roleId == null) {
            return java.util.Collections.emptyList();
        }

        return selectPermissionIdListByRoleIdList(
                java.util.Collections.singletonList(roleId)
        );
    }

    /**
     * 查询多个角色关联的权限标识。
     *
     * @param roleIds 角色标识列表
     * @return 权限标识列表
     */
    @Override
    public List<Long> selectPermissionIdListByRoleIdList(
            List<Long> roleIds) {

        if (roleIds == null || roleIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        return rolePermissionMapper.selectObjs(
                Wrappers.<RolePermissionPO>lambdaQuery()
                        .select(
                                RolePermissionPO::getPermissionId
                        )
                        .in(
                                RolePermissionPO::getRoleId,
                                roleIds
                        )
        )
                .stream()
                .map(DatabaseNumberUtils::toLong)
                .collect(java.util.stream.Collectors.toList());
    }
}
