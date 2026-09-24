package io.yak.framework.security.service;

import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.role.RoleAssignDTO;
import io.yak.framework.security.common.dto.role.RoleQueryDTO;
import io.yak.framework.security.common.dto.role.RoleSaveDTO;
import io.yak.framework.security.common.vo.role.AssignInfoVO;
import io.yak.framework.security.common.vo.role.RoleBriefVO;
import io.yak.framework.security.common.vo.role.RoleDeleteCheckVO;
import io.yak.framework.security.common.vo.role.RoleVO;
import io.yak.framework.security.exception.YakSecurityException;

import java.util.List;
import java.util.Map;

/**
 * 角色服务接口。
 *
 * @author weifuwan
 */
public interface RoleService {

  /**
   * 根据角色 ID 查询角色详情。
   *
   * @param roleId 角色 ID
   * @return 角色详情
   */
  RoleVO getRoleDetailByRoleId(
          Long roleId);

  /**
   * 根据角色 ID 查询角色简要信息。
   *
   * @param roleId 角色 ID
   * @return 角色简要信息
   */
  RoleBriefVO getRoleBriefByRoleId(
          Long roleId);

  /**
   * 分页查询角色。
   *
   * @param queryDTO 查询条件
   * @return 角色分页数据
   */
  PagingData<RoleVO> getRolePage(
          RoleQueryDTO queryDTO);

  /**
   * 创建角色。
   *
   * @param roleSaveDTO 角色信息
   * @param operator 操作人
   * @throws YakSecurityException 角色参数异常
   */
  void createRole(
          RoleSaveDTO roleSaveDTO,
          String operator)
          throws YakSecurityException;

  /**
   * 根据角色 ID 删除角色。
   *
   * @param roleId 角色 ID
   * @param operator 操作人
   * @throws YakSecurityException 角色不存在或仍有关联用户
   */
  void deleteRoleByRoleId(
          Long roleId,
          String operator)
          throws YakSecurityException;

  /**
   * 从角色中删除用户。
   *
   * @param roleId 角色 ID
   * @param userId 用户 ID
   * @param operator 操作人
   * @throws YakSecurityException 角色或参数异常
   */
  void deleteUserFromRole(
          Long roleId,
          Long userId,
          String operator)
          throws YakSecurityException;

  /**
   * 更新角色。
   *
   * @param roleSaveDTO 角色信息
   * @param operator 操作人
   * @throws YakSecurityException 角色不存在或参数异常
   */
  void updateRole(
          RoleSaveDTO roleSaveDTO,
          String operator)
          throws YakSecurityException;

  /**
   * 分配角色或为角色分配用户。
   *
   * @param assignDTO 分配参数
   * @param operator 操作人
   * @throws YakSecurityException 分配参数异常
   */
  void assignRoles(
          RoleAssignDTO assignDTO,
          String operator)
          throws YakSecurityException;

  /**
   * 根据角色 ID 查询用户分配信息。
   *
   * @param roleId 角色 ID
   * @return 用户分配信息列表
   */
  List<AssignInfoVO> getAssignInfoByRoleId(
          Long roleId);

  /**
   * 根据角色名称查询角色简要信息。
   *
   * @param roleName 角色名称
   * @return 角色简要信息列表
   */
  List<RoleBriefVO> getRoleBriefListByRoleName(
          String roleName);

  /**
   * 执行角色删除前校验。
   *
   * @param roleId 角色 ID
   * @return 删除校验结果
   */
  RoleDeleteCheckVO checkBeforeDelete(
          Long roleId);

  /**
   * 查询全部角色简要信息。
   *
   * @return 角色简要信息列表
   */
  List<RoleBriefVO> getAllRoleBriefList();

  /**
   * 根据用户 ID 查询角色简要信息。
   *
   * @param userId 用户 ID
   * @return 角色简要信息列表
   */
  List<RoleBriefVO> getRoleBriefListByUserId(
          Long userId);

  /**
   * 根据用户 ID 集合查询角色简要信息。
   *
   * @param userIdList 用户 ID 列表
   * @return 用户与角色列表映射
   */
  Map<Long, List<RoleBriefVO>>
  getRoleBriefListByUserIds(
          List<Long> userIdList);
}