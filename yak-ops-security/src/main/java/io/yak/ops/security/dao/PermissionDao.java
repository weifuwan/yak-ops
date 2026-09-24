package io.yak.ops.security.dao;

import io.yak.ops.security.common.entity.Permission;

import java.util.List;

/**
 * 权限数据访问接口。
 */
public interface PermissionDao {
    List<Permission> selectAllAndAscOrderByLevel();

    void insertBatch(List<Permission> var1);

    int deleteById(Long permissionId);

    /**
     * Adds, updates, reactivates and disables declaratively managed permissions.
     */
    void synchronizeDeclared(List<Permission> permissions);
}
