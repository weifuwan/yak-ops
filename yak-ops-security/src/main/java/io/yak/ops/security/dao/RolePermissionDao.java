package io.yak.ops.security.dao;

import io.yak.ops.security.common.entity.RolePermission;

import java.util.List;

/**
 * 角色权限关系数据访问接口。
 */
public interface RolePermissionDao {
    void insertBatch(List<RolePermission> var1);

    void deleteByRoleId(Long roleId);

    void deleteByPermissionId(Long permissionId);

    List<Long> selectPermissionIdListByRoleId(Long roleId);

    List<Long> selectPermissionIdListByRoleIdList(List<Long> var1);
}
