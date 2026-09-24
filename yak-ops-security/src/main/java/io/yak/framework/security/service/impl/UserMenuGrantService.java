package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.framework.security.common.po.MenuPO;
import io.yak.framework.security.common.po.RoleMenuPO;
import io.yak.framework.security.dao.mapper.MenuMapper;
import io.yak.framework.security.dao.mapper.RoleMenuMapper;
import io.yak.framework.security.service.RolePermissionService;
import io.yak.framework.security.service.UserRoleService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 解析用户的有效菜单授权。
 *
 * <p>显式菜单授权会展开启用的后代页面；按钮权限会自动补齐所属菜单；
 * 任一页面授权都会补齐父级目录。菜单只隐含页面读取权限，不会授予按钮。</p>
 */
@Service("yakSecurityUserMenuGrantService")
public class UserMenuGrantService {

  private final MenuMapper menuMapper;
  private final RoleMenuMapper roleMenuMapper;
  private final UserRoleService userRoleService;
  private final RolePermissionService rolePermissionService;
  private final PermissionMenuRelationService permissionMenuRelationService;

  public UserMenuGrantService(
      MenuMapper menuMapper,
      RoleMenuMapper roleMenuMapper,
      UserRoleService userRoleService,
      RolePermissionService rolePermissionService,
      PermissionMenuRelationService permissionMenuRelationService) {
    this.menuMapper = menuMapper;
    this.roleMenuMapper = roleMenuMapper;
    this.userRoleService = userRoleService;
    this.rolePermissionService = rolePermissionService;
    this.permissionMenuRelationService = permissionMenuRelationService;
  }

  /** 解析有效菜单编码和菜单隐含的读取权限编码。 */
  public MenuGrant resolve(Long userId) {
    if (userId == null) {
      return MenuGrant.empty();
    }

    List<Long> roleIds = normalizeIds(
        userRoleService.getRoleIdListByUserId(userId));
    List<Long> permissionIds = rolePermissionService
        .getPermissionIdListByRoleIdList(roleIds);
    return resolve(roleIds, permissionIds);
  }

  /**
   * 基于已加载的角色和权限解析菜单授权。
   *
   * <p>授权快照构建时使用本入口，避免再次查询用户角色和角色权限关系。</p>
   */
  MenuGrant resolve(
      Collection<Long> roleIds,
      Collection<Long> permissionIds) {
    List<Long> normalizedRoleIds = normalizeIds(roleIds);
    if (normalizedRoleIds.isEmpty()) {
      return MenuGrant.empty();
    }

    List<MenuPO> menus = menuMapper.selectList(
        Wrappers.<MenuPO>lambdaQuery()
            .orderByAsc(MenuPO::getSortOrder)
            .orderByAsc(MenuPO::getId));
    if (CollectionUtils.isEmpty(menus)) {
      return MenuGrant.empty();
    }

    Map<Long, MenuPO> byId = new LinkedHashMap<>();
    Map<String, MenuPO> byCode = new LinkedHashMap<>();
    Map<String, List<MenuPO>> childrenByParentCode = new HashMap<>();

    for (MenuPO menu : menus) {
      if (menu == null
          || menu.getId() == null
          || !StringUtils.hasText(menu.getMenuCode())) {
        continue;
      }
      byId.put(menu.getId(), menu);
      byCode.put(menu.getMenuCode(), menu);
      if (StringUtils.hasText(menu.getParentCode())) {
        childrenByParentCode
            .computeIfAbsent(
                menu.getParentCode(),
                ignored -> new ArrayList<>())
            .add(menu);
      }
    }

    Set<Long> selectedMenuIds = new LinkedHashSet<>();
    List<RoleMenuPO> relations = roleMenuMapper.selectList(
        Wrappers.<RoleMenuPO>lambdaQuery()
            .in(RoleMenuPO::getRoleId, normalizedRoleIds));
    if (!CollectionUtils.isEmpty(relations)) {
      relations.stream()
          .map(RoleMenuPO::getMenuId)
          .filter(Objects::nonNull)
          .filter(id -> id > 0L)
          .forEach(selectedMenuIds::add);
    }

    // 运行时兜底：按钮权限本身即可推导所属菜单，不依赖角色菜单关系是否完整。
    selectedMenuIds.addAll(
        permissionMenuRelationService.inferMenuIds(permissionIds));

    if (selectedMenuIds.isEmpty()) {
      return MenuGrant.empty();
    }

    Set<String> effectiveCodes = new LinkedHashSet<>();
    for (Long menuId : selectedMenuIds) {
      MenuPO selected = byId.get(menuId);
      if (!isActive(selected)) {
        continue;
      }
      addDescendants(
          selected,
          childrenByParentCode,
          effectiveCodes,
          new HashSet<>());
    }

    Set<String> snapshot = new LinkedHashSet<>(effectiveCodes);
    for (String code : snapshot) {
      addParents(
          byCode.get(code),
          byCode,
          effectiveCodes,
          new HashSet<>());
    }

    Set<String> permissionCodes = new LinkedHashSet<>();
    for (String code : effectiveCodes) {
      MenuPO menu = byCode.get(code);
      if (isActive(menu)
          && StringUtils.hasText(menu.getRequiredPermissionCode())) {
        permissionCodes.add(menu.getRequiredPermissionCode());
      }
    }

    return new MenuGrant(
        new ArrayList<>(effectiveCodes),
        new ArrayList<>(permissionCodes));
  }

  public List<String> getMenuCodesByUserId(Long userId) {
    return resolve(userId).getMenuCodes();
  }

  public List<String> getPermissionCodesByUserId(Long userId) {
    return resolve(userId).getPermissionCodes();
  }

  private void addDescendants(
      MenuPO menu,
      Map<String, List<MenuPO>> childrenByParentCode,
      Set<String> result,
      Set<String> visited) {
    if (!isActive(menu) || !visited.add(menu.getMenuCode())) {
      return;
    }

    result.add(menu.getMenuCode());
    for (MenuPO child : childrenByParentCode.getOrDefault(
        menu.getMenuCode(),
        Collections.emptyList())) {
      addDescendants(
          child,
          childrenByParentCode,
          result,
          visited);
    }
  }

  private void addParents(
      MenuPO menu,
      Map<String, MenuPO> byCode,
      Set<String> result,
      Set<String> visited) {
    MenuPO current = menu;
    while (isActive(current)
        && visited.add(current.getMenuCode())) {
      result.add(current.getMenuCode());
      current = StringUtils.hasText(current.getParentCode())
          ? byCode.get(current.getParentCode())
          : null;
    }
  }

  /** visible 只控制侧边栏展示，隐藏页面仍可参与授权。 */
  private boolean isActive(MenuPO menu) {
    return menu != null
        && Boolean.TRUE.equals(menu.getActive())
        && StringUtils.hasText(menu.getMenuCode());
  }

  private List<Long> normalizeIds(Collection<Long> values) {
    if (CollectionUtils.isEmpty(values)) {
      return new ArrayList<>();
    }
    return values.stream()
        .filter(Objects::nonNull)
        .filter(value -> value > 0L)
        .distinct()
        .collect(Collectors.toList());
  }

  /** 有效授权结果。 */
  public static final class MenuGrant {
    private final List<String> menuCodes;
    private final List<String> permissionCodes;

    private MenuGrant(
        List<String> menuCodes,
        List<String> permissionCodes) {
      this.menuCodes = Collections.unmodifiableList(
          new ArrayList<>(menuCodes));
      this.permissionCodes = Collections.unmodifiableList(
          new ArrayList<>(permissionCodes));
    }

    public static MenuGrant empty() {
      return new MenuGrant(
          Collections.emptyList(),
          Collections.emptyList());
    }

    public List<String> getMenuCodes() {
      return menuCodes;
    }

    public List<String> getPermissionCodes() {
      return permissionCodes;
    }
  }
}
