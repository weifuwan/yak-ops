package io.yak.framework.security.service;

import java.util.List;

/**
 * 角色权限关系服务接口。
 *
 * @author weifuwan
 */
public interface RolePermissionService {

  /**
   * 保存角色权限关系。
   *
   * @param roleId 角色 ID
   * @param permissionIdList 权限 ID 列表
   */
  void saveRolePermission(
          Long roleId,
          List<Long> permissionIdList);

  /**
   * 全量更新角色权限关系。
   *
   * <p>权限 ID 列表为空时，清空该角色的全部权限。
   *
   * @param roleId 角色 ID
   * @param permissionIdList 权限 ID 列表
   */
  void updateRolePermission(
          Long roleId,
          List<Long> permissionIdList);

  /**
   * 根据角色 ID 删除角色权限关系。
   *
   * @param roleId 角色 ID
   */
  void deleteRolePermissionByRoleId(
          Long roleId);

  /** 根据权限 ID 删除角色权限关系。 */
  void deleteRolePermissionByPermissionId(
          Long permissionId);

  /**
   * 根据角色 ID 查询权限 ID。
   *
   * @param roleId 角色 ID
   * @return 权限 ID 列表
   */
  List<Long> getPermissionIdListByRoleId(
          Long roleId);

  /**
   * 根据角色 ID 集合查询权限 ID。
   *
   * @param roleIdList 角色 ID 列表
   * @return 权限 ID 列表
   */
  List<Long> getPermissionIdListByRoleIdList(
          List<Long> roleIdList);
}
