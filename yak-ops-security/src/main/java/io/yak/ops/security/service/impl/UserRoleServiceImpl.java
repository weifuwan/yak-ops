package io.yak.ops.security.service.impl;

import io.yak.ops.security.common.entity.UserRole;
import io.yak.ops.security.dao.UserRoleDao;
import io.yak.ops.security.service.UserRoleService;
import io.yak.ops.security.service.PermissionCache;
import io.yak.ops.security.util.CopyBeanUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * 用户角色关系服务实现类。
 *
 * @author weifuwan
 */
@Service("yakSecurityUserRoleServiceImpl")
public class UserRoleServiceImpl implements UserRoleService {

  private final UserRoleDao userRoleDao;
  private final PermissionCache permissionCache;

  /**
   * 创建用户角色关系服务。
   *
   * @param userRoleDao 用户角色关系数据访问对象
   */
  public UserRoleServiceImpl(
          UserRoleDao userRoleDao,
          PermissionCache permissionCache) {

    this.userRoleDao = userRoleDao;
    this.permissionCache = permissionCache;
  }

  /**
   * 根据角色 ID 查询用户 ID。
   *
   * @param roleId 角色 ID
   * @return 用户 ID 列表
   */
  @Override
  public List<Long> getUserIdListByRoleId(
          Long roleId) {

    if (roleId == null) {
      return new ArrayList<>();
    }

    List<Long> userIdList =
            userRoleDao.selectUserIdListByRoleId(
                    roleId);

    return userIdList == null
            ? new ArrayList<>()
            : userIdList;
  }

  /**
   * 根据用户 ID 查询角色 ID。
   *
   * @param userId 用户 ID
   * @return 角色 ID 列表
   */
  @Override
  public List<Long> getRoleIdListByUserId(
          Long userId) {

    if (userId == null) {
      return new ArrayList<>();
    }

    List<Long> roleIdList =
            userRoleDao.selectRoleIdListByUserId(
                    userId);

    return roleIdList == null
            ? new ArrayList<>()
            : roleIdList;
  }

  /**
   * 根据用户 ID 更新用户角色关系。
   *
   * @param userId 用户 ID
   * @param roleIdList 角色 ID 列表
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void updateUserRoleByUserId(
          Long userId,
          List<Long> roleIdList) {

    if (userId == null) {
      return;
    }

    /*
     * 必须先删除旧关系。
     *
     * 当 roleIdList 为空时，表示清空该用户的全部角色。
     */
    userRoleDao.deleteByUserIdOrRoleId(
            userId,
            null);
    permissionCache.invalidateUser(userId);

    List<Long> validRoleIds =
            normalizeIds(roleIdList);

    if (validRoleIds.isEmpty()) {
      return;
    }

    userRoleDao.insertBatch(
            buildByUserId(
                    userId,
                    validRoleIds));
  }

  /**
   * 根据角色 ID 更新角色用户关系。
   *
   * @param roleId 角色 ID
   * @param userIdList 用户 ID 列表
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void updateUserRoleByRoleId(
          Long roleId,
          List<Long> userIdList) {

    if (roleId == null) {
      return;
    }

    permissionCache.invalidateRole(roleId);

    /*
     * 用户列表为空时，表示清空该角色关联的全部用户。
     */
    userRoleDao.deleteByUserIdOrRoleId(
            null,
            roleId);

    List<Long> validUserIds =
            normalizeIds(userIdList);

    if (validUserIds.isEmpty()) {
      return;
    }

    userRoleDao.insertBatch(
            buildByRoleId(
                    roleId,
                    validUserIds));
  }

  /**
   * 根据角色 ID 查询用户角色关系数量。
   *
   * @param roleId 角色 ID
   * @return 用户角色关系数量
   */
  @Override
  public int getUserRoleCountByRoleId(
          Long roleId) {

    /*
     * 原代码 roleId == 0 会触发 Long 自动拆箱，
     * roleId 为空时会产生 NullPointerException。
     */
    if (roleId == null
            || Objects.equals(roleId, 0L)) {

      return 0;
    }

    return userRoleDao.selectCountByRoleId(
            roleId);
  }

  /**
   * 根据用户 ID 或角色 ID 删除用户角色关系。
   *
   * @param userId 用户 ID
   * @param roleId 角色 ID
   * @return 删除记录数
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public int deleteByUserIdOrRoleId(
          Long userId,
          Long roleId) {

    /*
     * 避免两个条件同时为空时误删全部关系数据。
     */
    if (userId == null && roleId == null) {
      return 0;
    }

    if (userId != null) {
      permissionCache.invalidateUser(userId);
    } else {
      permissionCache.invalidateRole(roleId);
    }
    return userRoleDao.deleteByUserIdOrRoleId(
            userId,
            roleId);
  }

  /**
   * 根据角色 ID 集合查询用户角色关系。
   *
   * @param roleIdList 角色 ID 列表
   * @return 用户角色关系列表
   */
  @Override
  public List<UserRole> getByRoleIds(
          List<Long> roleIdList) {

    List<Long> validRoleIds =
            normalizeIds(roleIdList);

    if (validRoleIds.isEmpty()) {
      return new ArrayList<>();
    }

    return CopyBeanUtil.copyList(
            userRoleDao.selectByRoleIds(
                    validRoleIds),
            UserRole.class);
  }

  /**
   * 根据用户 ID 集合查询用户角色关系。
   *
   * @param userIdList 用户 ID 列表
   * @return 用户角色关系列表
   */
  @Override
  public List<UserRole> getRoleIdListByUserIds(
          List<Long> userIdList) {

    List<Long> validUserIds =
            normalizeIds(userIdList);

    if (validUserIds.isEmpty()) {
      return new ArrayList<>();
    }

    return CopyBeanUtil.copyList(
            userRoleDao.getRoleIdListByUserIds(
                    validUserIds),
            UserRole.class);
  }

  /**
   * 根据用户 ID 构建用户角色关系。
   *
   * @param userId 用户 ID
   * @param roleIdList 角色 ID 列表
   * @return 用户角色关系列表
   */
  private List<UserRole> buildByUserId(
          Long userId,
          List<Long> roleIdList) {

    List<UserRole> userRoleList =
            new ArrayList<>(roleIdList.size());

    for (Long roleId : roleIdList) {
      userRoleList.add(
              new UserRole(
                      userId,
                      roleId));
    }

    return userRoleList;
  }

  /**
   * 根据角色 ID 构建用户角色关系。
   *
   * @param roleId 角色 ID
   * @param userIdList 用户 ID 列表
   * @return 用户角色关系列表
   */
  private List<UserRole> buildByRoleId(
          Long roleId,
          List<Long> userIdList) {

    List<UserRole> userRoleList =
            new ArrayList<>(userIdList.size());

    for (Long userId : userIdList) {
      userRoleList.add(
              new UserRole(
                      userId,
                      roleId));
    }

    return userRoleList;
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
