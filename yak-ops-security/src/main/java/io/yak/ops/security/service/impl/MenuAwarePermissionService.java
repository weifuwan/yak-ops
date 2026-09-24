package io.yak.ops.security.service.impl;

import io.yak.ops.security.common.dto.permission.PermissionDTO;
import io.yak.ops.security.common.vo.permission.PermissionTreeVO;
import io.yak.ops.security.service.PermissionService;
import io.yak.ops.security.service.RolePermissionService;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 将角色权限树整理为“目录 → 菜单 → 按钮”的统一能力树。
 */
@Primary
@Service("yakSecurityMenuAwarePermissionService")
public class MenuAwarePermissionService implements PermissionService {

  private final PermissionService delegate;
  private final RolePermissionService rolePermissionService;
  private final MenuAuthorizationService menuAuthorizationService;
  private final PermissionMenuRelationService permissionMenuRelationService;

  public MenuAwarePermissionService(
      @Qualifier("yakSecurityPermissionServiceImpl")
          PermissionService delegate,
      RolePermissionService rolePermissionService,
      MenuAuthorizationService menuAuthorizationService,
      PermissionMenuRelationService permissionMenuRelationService) {
    this.delegate = delegate;
    this.rolePermissionService = rolePermissionService;
    this.menuAuthorizationService = menuAuthorizationService;
    this.permissionMenuRelationService = permissionMenuRelationService;
  }

  @Override
  public PermissionTreeVO buildPermissionTreeWithHas(
      List<Long> permissionIdList) {
    List<Long> normalPermissionIds =
        MenuSelectionCodec.extractPermissionIds(permissionIdList);
    Set<Long> menuManagedPermissionIds =
        menuAuthorizationService.getMenuBoundPermissionIds();
    normalPermissionIds.removeIf(menuManagedPermissionIds::contains);

    PermissionTreeVO root = delegate
        .buildPermissionTreeWithHas(normalPermissionIds);
    return mergeMenuTree(
        root,
        MenuSelectionCodec.extractMenuIds(permissionIdList),
        normalPermissionIds);
  }

  @Override
  public PermissionTreeVO buildPermissionTree() {
    return mergeMenuTree(
        delegate.buildPermissionTree(),
        Collections.emptyList(),
        Collections.emptyList());
  }

  @Override
  public PermissionTreeVO buildPermissionTreeByRoleId(Long roleId) {
    List<Long> permissionIds = roleId == null
        ? Collections.emptyList()
        : rolePermissionService.getPermissionIdListByRoleId(roleId);
    List<Long> normalPermissionIds =
        MenuSelectionCodec.extractPermissionIds(permissionIds);
    Set<Long> menuManagedPermissionIds =
        menuAuthorizationService.getMenuBoundPermissionIds();
    normalPermissionIds.removeIf(menuManagedPermissionIds::contains);

    return mergeMenuTree(
        delegate.buildPermissionTreeWithHas(normalPermissionIds),
        menuAuthorizationService.getMenuIdsByRoleId(roleId),
        normalPermissionIds);
  }

  @Override
  public void savePermission(List<PermissionDTO> permissionDTOList) {
    delegate.savePermission(permissionDTOList);
  }

  @Override
  public void deletePermissionById(Long permissionId) {
    delegate.deletePermissionById(permissionId);
  }

  private PermissionTreeVO mergeMenuTree(
      PermissionTreeVO root,
      Collection<Long> selectedMenuIds,
      Collection<Long> selectedPermissionIds) {
    Set<String> menuBoundPermissionCodes =
        menuAuthorizationService.getMenuBoundPermissionCodes();
    filterMenuBoundPermissionNodes(
        root,
        menuBoundPermissionCodes,
        true);

    PermissionTreeVO menuTree =
        menuAuthorizationService.buildMenuTree(selectedMenuIds);
    return permissionMenuRelationService.mergeCapabilityTree(
        root,
        menuTree,
        selectedPermissionIds);
  }

  private boolean filterMenuBoundPermissionNodes(
      PermissionTreeVO node,
      Set<String> menuBoundPermissionCodes,
      boolean root) {
    if (node == null) {
      return false;
    }
    if (menuBoundPermissionCodes.contains(node.getPermissionCode())) {
      return false;
    }

    List<PermissionTreeVO> children = node.getChildList();
    if (children != null) {
      children.removeIf(child ->
          !filterMenuBoundPermissionNodes(
              child,
              menuBoundPermissionCodes,
              false));
    }

    return root
        || Boolean.TRUE.equals(node.getLeaf())
        || (node.getChildList() != null
            && !node.getChildList().isEmpty());
  }
}
