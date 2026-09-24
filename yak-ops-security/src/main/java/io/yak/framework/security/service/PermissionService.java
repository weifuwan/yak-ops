package io.yak.framework.security.service;

import io.yak.framework.security.common.dto.permission.PermissionDTO;
import io.yak.framework.security.common.vo.permission.PermissionTreeVO;

import java.util.List;

/**
 * 权限服务接口。
 *
 * @author weifuwan
 */
public interface PermissionService {

  /**
   * 构建包含选中状态的权限树。
   *
   * @param permissionIdList 已选中的权限 ID 列表
   * @return 权限树
   */
  PermissionTreeVO buildPermissionTreeWithHas(
          List<Long> permissionIdList);

  /**
   * 构建权限树。
   *
   * @return 权限树
   */
  PermissionTreeVO buildPermissionTree();

  /**
   * 根据角色 ID 构建包含选中状态的权限树。
   *
   * @param roleId 角色 ID
   * @return 权限树
   */
  PermissionTreeVO buildPermissionTreeByRoleId(
          Long roleId);

  /**
   * 批量保存权限树。
   *
   * @param permissionDTOList 权限树数据
   */
  void savePermission(
          List<PermissionDTO> permissionDTOList);

  /**
   * 根据权限 ID 删除权限及其角色关联。
   *
   * @param permissionId 权限 ID
   */
  void deletePermissionById(Long permissionId);
}
