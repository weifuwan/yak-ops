package io.yak.ops.security.service.impl;

import io.yak.ops.security.common.entity.RolePermission;
import io.yak.ops.security.dao.RolePermissionDao;
import io.yak.ops.security.service.RolePermissionService;
import io.yak.ops.security.service.PermissionCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * 角色权限关系服务实现类。
 *
 * @author weifuwan
 */
@Service("yakSecurityRolePermissionServiceImpl")
public class RolePermissionServiceImpl
        implements RolePermissionService {

  private final RolePermissionDao rolePermissionDao;
  private final PermissionCache permissionCache;

  /**
   * 创建角色权限关系服务。
   *
   * @param rolePermissionDao 角色权限关系数据访问对象
   */
  public RolePermissionServiceImpl(
          RolePermissionDao rolePermissionDao,
          PermissionCache permissionCache) {

    this.rolePermissionDao = rolePermissionDao;
    this.permissionCache = permissionCache;
  }

  /**
   * 保存角色权限关系。
   *
   * @param roleId 角色 ID
   * @param permissionIdList 权限 ID 列表
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void saveRolePermission(
          Long roleId,
          List<Long> permissionIdList) {

    if (roleId == null) {
      return;
    }
    permissionCache.invalidateRole(roleId);

    List<Long> validPermissionIds =
            normalizeIds(permissionIdList);

    if (validPermissionIds.isEmpty()) {
      return;
    }

    List<RolePermission> rolePermissionList =
            buildRolePermissionList(
                    roleId,
                    validPermissionIds);

    rolePermissionDao.insertBatch(
            rolePermissionList);
  }

  /**
   * 全量更新角色权限关系。
   *
   * @param roleId 角色 ID
   * @param permissionIdList 权限 ID 列表
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void updateRolePermission(
          Long roleId,
          List<Long> permissionIdList) {

    if (roleId == null) {
      return;
    }
    permissionCache.invalidateRole(roleId);

    /*
     * 先删除原有关联。
     *
     * permissionIdList 为空时，表示清空该角色的全部权限。
     */
    rolePermissionDao.deleteByRoleId(
            roleId);

    List<Long> validPermissionIds =
            normalizeIds(permissionIdList);

    if (validPermissionIds.isEmpty()) {
      return;
    }

    rolePermissionDao.insertBatch(
            buildRolePermissionList(
                    roleId,
                    validPermissionIds));
  }

  /**
   * 根据角色 ID 删除角色权限关系。
   *
   * @param roleId 角色 ID
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void deleteRolePermissionByRoleId(
          Long roleId) {

    if (roleId == null) {
      return;
    }
    permissionCache.invalidateRole(roleId);

    rolePermissionDao.deleteByRoleId(
            roleId);
  }

  /** 根据权限 ID 删除角色权限关系。 */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void deleteRolePermissionByPermissionId(
          Long permissionId) {

    if (permissionId == null) {
      return;
    }
    permissionCache.invalidateAll();
    rolePermissionDao.deleteByPermissionId(permissionId);
  }

  /**
   * 根据角色 ID 查询权限 ID。
   *
   * @param roleId 角色 ID
   * @return 权限 ID 列表
   */
  @Override
  public List<Long> getPermissionIdListByRoleId(
          Long roleId) {

    if (roleId == null) {
      return new ArrayList<>();
    }

    List<Long> permissionIdList =
            rolePermissionDao
                    .selectPermissionIdListByRoleId(
                            roleId);

    return permissionIdList == null
            ? new ArrayList<>()
            : permissionIdList;
  }

  /**
   * 根据角色 ID 集合查询权限 ID。
   *
   * @param roleIdList 角色 ID 列表
   * @return 权限 ID 列表
   */
  @Override
  public List<Long> getPermissionIdListByRoleIdList(
          List<Long> roleIdList) {

    List<Long> validRoleIds =
            normalizeIds(roleIdList);

    if (validRoleIds.isEmpty()) {
      return new ArrayList<>();
    }

    List<Long> permissionIdList =
            rolePermissionDao
                    .selectPermissionIdListByRoleIdList(
                            validRoleIds);

    if (CollectionUtils.isEmpty(permissionIdList)) {
      return new ArrayList<>();
    }

    /*
     * 多个角色可能具有相同权限，
     * 服务层统一去重，避免重复构建权限树。
     */
    return permissionIdList.stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
  }

  /**
   * 构建角色权限关系列表。
   *
   * @param roleId 角色 ID
   * @param permissionIdList 权限 ID 列表
   * @return 角色权限关系列表
   */
  private List<RolePermission> buildRolePermissionList(
          Long roleId,
          List<Long> permissionIdList) {

    List<RolePermission> rolePermissionList =
            new ArrayList<>(
                    permissionIdList.size());

    for (Long permissionId : permissionIdList) {
      RolePermission rolePermission =
              new RolePermission();

      rolePermission.setRoleId(roleId);
      rolePermission.setPermissionId(
              permissionId);

      rolePermissionList.add(
              rolePermission);
    }

    return rolePermissionList;
  }

  /**
   * 过滤空 ID 并去重。
   *
   * @param idList ID 列表
   * @return 有效 ID 列表
   */
  private List<Long> normalizeIds(
          List<Long> idList) {

    if (CollectionUtils.isEmpty(idList)) {
      return new ArrayList<>();
    }

    return idList.stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
  }
}
