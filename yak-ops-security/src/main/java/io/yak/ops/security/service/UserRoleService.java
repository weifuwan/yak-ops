package io.yak.ops.security.service;

import io.yak.ops.security.common.entity.UserRole;

import java.util.List;

/**
 * 用户角色关系服务接口。
 *
 * @author weifuwan
 */
public interface UserRoleService {

  /**
   * 根据角色 ID 查询用户 ID。
   *
   * @param roleId 角色 ID
   * @return 用户 ID 列表
   */
  List<Long> getUserIdListByRoleId(
          Long roleId);

  /**
   * 根据用户 ID 查询角色 ID。
   *
   * @param userId 用户 ID
   * @return 角色 ID 列表
   */
  List<Long> getRoleIdListByUserId(
          Long userId);

  /**
   * 根据用户 ID 更新用户角色关系。
   *
   * <p>角色 ID 列表为空时，清空该用户的全部角色。
   *
   * @param userId 用户 ID
   * @param roleIdList 角色 ID 列表
   */
  void updateUserRoleByUserId(
          Long userId,
          List<Long> roleIdList);

  /**
   * 根据角色 ID 更新角色用户关系。
   *
   * <p>用户 ID 列表为空时，清空该角色关联的全部用户。
   *
   * @param roleId 角色 ID
   * @param userIdList 用户 ID 列表
   */
  void updateUserRoleByRoleId(
          Long roleId,
          List<Long> userIdList);

  /**
   * 根据角色 ID 查询用户角色关系数量。
   *
   * @param roleId 角色 ID
   * @return 用户角色关系数量
   */
  int getUserRoleCountByRoleId(
          Long roleId);

  /**
   * 根据用户 ID 或角色 ID 删除用户角色关系。
   *
   * <p>用户 ID 和角色 ID 不能同时为空。
   *
   * @param userId 用户 ID
   * @param roleId 角色 ID
   * @return 删除记录数
   */
  int deleteByUserIdOrRoleId(
          Long userId,
          Long roleId);

  /**
   * 根据角色 ID 集合查询用户角色关系。
   *
   * @param roleIdList 角色 ID 列表
   * @return 用户角色关系列表
   */
  List<UserRole> getByRoleIds(
          List<Long> roleIdList);

  /**
   * 根据用户 ID 集合查询用户角色关系。
   *
   * <p>保留原有方法名称，避免影响现有调用方。
   *
   * @param userIdList 用户 ID 列表
   * @return 用户角色关系列表
   */
  List<UserRole> getRoleIdListByUserIds(
          List<Long> userIdList);
}