package io.yak.ops.security.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.security.common.entity.Permission;
import io.yak.ops.security.common.po.MenuPO;
import io.yak.ops.security.common.po.RoleMenuPO;
import io.yak.ops.security.common.vo.permission.PermissionTreeVO;
import io.yak.ops.security.dao.PermissionDao;
import io.yak.ops.security.dao.mapper.MenuMapper;
import io.yak.ops.security.dao.mapper.RoleMenuMapper;
import io.yak.ops.security.service.PermissionCache;
import io.yak.ops.security.service.UserRoleService;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 菜单定义、角色菜单关系和菜单隐含读取权限的统一服务。
 */
@Service("yakSecurityMenuAuthorizationService")
public class MenuAuthorizationService {

  private static final String MENU_PERMISSION_PREFIX = "menu:";

  private final MenuMapper menuMapper;
  private final RoleMenuMapper roleMenuMapper;
  private final PermissionDao permissionDao;
  private final UserRoleService userRoleService;
  private final PermissionCache permissionCache;

  public MenuAuthorizationService(
          MenuMapper menuMapper,
          RoleMenuMapper roleMenuMapper,
          PermissionDao permissionDao,
          UserRoleService userRoleService,
          PermissionCache permissionCache) {

    this.menuMapper = menuMapper;
    this.roleMenuMapper = roleMenuMapper;
    this.permissionDao = permissionDao;
    this.userRoleService = userRoleService;
    this.permissionCache = permissionCache;
  }

  /** 查询菜单定义，包含停用记录，供角色管理树完整展示。 */
  public List<MenuPO> listMenus() {
    List<MenuPO> menus = menuMapper.selectList(
            Wrappers.<MenuPO>lambdaQuery()
                    .orderByAsc(MenuPO::getSortOrder)
                    .orderByAsc(MenuPO::getId));

    return menus == null ? new ArrayList<>() : menus;
  }

  /** 查询一个角色已分配的菜单 ID。 */
  public List<Long> getMenuIdsByRoleId(Long roleId) {
    if (roleId == null) {
      return new ArrayList<>();
    }
    return getMenuIdsByRoleIds(Collections.singletonList(roleId));
  }

  /** 查询多个角色已分配的菜单 ID。 */
  public List<Long> getMenuIdsByRoleIds(Collection<Long> roleIds) {
    List<Long> normalizedRoleIds = normalizePositiveIds(roleIds);
    if (normalizedRoleIds.isEmpty()) {
      return new ArrayList<>();
    }

    List<RoleMenuPO> relations = roleMenuMapper.selectList(
            Wrappers.<RoleMenuPO>lambdaQuery()
                    .in(RoleMenuPO::getRoleId, normalizedRoleIds));

    if (CollectionUtils.isEmpty(relations)) {
      return new ArrayList<>();
    }

    return relations.stream()
            .map(RoleMenuPO::getMenuId)
            .filter(Objects::nonNull)
            .filter(id -> id > 0L)
            .distinct()
            .collect(Collectors.toList());
  }

  /** 全量保存新角色的菜单关系。 */
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void saveRoleMenus(
          Long roleId,
          Collection<Long> menuIds) {

    if (roleId == null) {
      return;
    }
    insertRoleMenus(roleId, menuIds);
    permissionCache.invalidateRole(roleId);
  }

  /** 全量替换角色菜单关系。 */
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void updateRoleMenus(
          Long roleId,
          Collection<Long> menuIds) {

    if (roleId == null) {
      return;
    }

    roleMenuMapper.delete(
            Wrappers.<RoleMenuPO>lambdaQuery()
                    .eq(RoleMenuPO::getRoleId, roleId));
    insertRoleMenus(roleId, menuIds);
    permissionCache.invalidateRole(roleId);
  }

  /** 删除角色时同步清理菜单关系。 */
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void deleteRoleMenus(Long roleId) {
    if (roleId == null) {
      return;
    }

    roleMenuMapper.delete(
            Wrappers.<RoleMenuPO>lambdaQuery()
                    .eq(RoleMenuPO::getRoleId, roleId));
    permissionCache.invalidateRole(roleId);
  }

  /**
   * 返回全部由菜单管理的读取权限 ID。
   *
   * <p>这些权限不再由普通操作权限树直接控制，避免“勾了读取权限但没勾菜单”
   * 或“取消菜单后仍可访问页面”两套授权来源互相冲突。
   */
  public Set<Long> getMenuBoundPermissionIds() {
    Set<String> codes = getMenuBoundPermissionCodes();
    if (codes.isEmpty()) {
      return Collections.emptySet();
    }

    return activePermissionsByCode().entrySet().stream()
            .filter(entry -> codes.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** 查询角色菜单隐含授予的读取权限 ID。 */
  public List<Long> getRequiredPermissionIdsByRoleId(Long roleId) {
    return getRequiredPermissionIdsByRoleIds(
            roleId == null
                    ? Collections.emptyList()
                    : Collections.singletonList(roleId));
  }

  /** 查询多个角色菜单隐含授予的读取权限 ID。 */
  public List<Long> getRequiredPermissionIdsByRoleIds(
          Collection<Long> roleIds) {

    List<Long> menuIds = getMenuIdsByRoleIds(roleIds);
    if (menuIds.isEmpty()) {
      return new ArrayList<>();
    }

    Map<Long, MenuPO> menusById = menusByIds(menuIds);
    Map<String, Long> permissionsByCode = activePermissionsByCode();
    Set<Long> result = new LinkedHashSet<>();

    for (Long menuId : menuIds) {
      MenuPO menu = menusById.get(menuId);
      if (!isAvailable(menu)
              || !StringUtils.hasText(
              menu.getRequiredPermissionCode())) {
        continue;
      }

      Long permissionId = permissionsByCode.get(
              menu.getRequiredPermissionCode());
      if (permissionId != null) {
        result.add(permissionId);
      }
    }

    return new ArrayList<>(result);
  }

  /** 返回菜单绑定的读取权限编码，供权限树隐藏重复的读取节点。 */
  public Set<String> getMenuBoundPermissionCodes() {
    return listMenus().stream()
            .map(MenuPO::getRequiredPermissionCode)
            .filter(StringUtils::hasText)
            .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** 获取当前用户可见的菜单编码，并自动补齐父目录编码。 */
  public List<String> getMenuCodesByUserId(Long userId) {
    if (userId == null) {
      return new ArrayList<>();
    }

    List<Long> roleIds = userRoleService.getRoleIdListByUserId(userId);
    List<Long> menuIds = getMenuIdsByRoleIds(roleIds);
    if (menuIds.isEmpty()) {
      return new ArrayList<>();
    }

    List<MenuPO> menus = listMenus();
    Map<Long, MenuPO> byId = menus.stream()
            .filter(menu -> menu.getId() != null)
            .collect(Collectors.toMap(
                    MenuPO::getId,
                    menu -> menu,
                    (left, right) -> left,
                    LinkedHashMap::new));
    Map<String, MenuPO> byCode = menus.stream()
            .filter(menu -> StringUtils.hasText(menu.getMenuCode()))
            .collect(Collectors.toMap(
                    MenuPO::getMenuCode,
                    menu -> menu,
                    (left, right) -> left,
                    LinkedHashMap::new));

    Set<String> result = new LinkedHashSet<>();
    for (Long menuId : menuIds) {
      MenuPO menu = byId.get(menuId);
      if (!isAvailable(menu)) {
        continue;
      }

      addMenuAndParents(menu, byCode, result);
    }
    return new ArrayList<>(result);
  }

  /** 构建可直接并入现有 PermissionTreeVO 的菜单权限树。 */
  public PermissionTreeVO buildMenuTree(
          Collection<Long> selectedMenuIds) {

    Set<Long> selected = new HashSet<>(
            normalizePositiveIds(selectedMenuIds));
    PermissionTreeVO group = PermissionTreeVO.builder()
            .id(MenuSelectionCodec.MENU_GROUP_NODE_ID)
            .has(Boolean.TRUE)
            .permissionCode("menu")
            .permissionName("菜单权限")
            .parentId(0L)
            .leaf(Boolean.FALSE)
            .description("控制角色登录后可见和可访问的页面入口")
            .active(Boolean.TRUE)
            .declared(Boolean.FALSE)
            .childList(new ArrayList<>())
            .build();

    List<MenuPO> menus = listMenus();
    if (menus.isEmpty()) {
      return group;
    }

    Map<String, PermissionTreeVO> nodesByCode = new LinkedHashMap<>();
    Map<String, MenuPO> menusByCode = new LinkedHashMap<>();

    for (MenuPO menu : menus) {
      if (menu == null
              || menu.getId() == null
              || !StringUtils.hasText(menu.getMenuCode())) {
        continue;
      }

      PermissionTreeVO node = PermissionTreeVO.builder()
              .id(MenuSelectionCodec.encodeMenuId(menu.getId()))
              .has(selected.contains(menu.getId()))
              .permissionCode(MENU_PERMISSION_PREFIX + menu.getMenuCode())
              .permissionName(menu.getMenuName())
              .leaf(Boolean.TRUE)
              .description(buildDescription(menu))
              .active(isAvailable(menu))
              .declared(Boolean.FALSE)
              .childList(new ArrayList<>())
              .build();

      nodesByCode.put(menu.getMenuCode(), node);
      menusByCode.put(menu.getMenuCode(), menu);
    }

    for (Map.Entry<String, PermissionTreeVO> entry
            : nodesByCode.entrySet()) {
      MenuPO menu = menusByCode.get(entry.getKey());
      PermissionTreeVO node = entry.getValue();
      PermissionTreeVO parent = StringUtils.hasText(menu.getParentCode())
              ? nodesByCode.get(menu.getParentCode())
              : group;

      if (parent == null) {
        parent = group;
      }

      node.setParentId(parent.getId());
      parent.setLeaf(Boolean.FALSE);
      parent.getChildList().add(node);
    }

    return group;
  }

  private void insertRoleMenus(
          Long roleId,
          Collection<Long> menuIds) {

    List<Long> normalizedMenuIds = normalizePositiveIds(menuIds);
    if (normalizedMenuIds.isEmpty()) {
      return;
    }

    Map<Long, MenuPO> existingMenus = menusByIds(normalizedMenuIds);
    for (Long menuId : normalizedMenuIds) {
      MenuPO menu = existingMenus.get(menuId);
      if (menu == null || !Boolean.TRUE.equals(menu.getActive())) {
        continue;
      }

      RoleMenuPO relation = new RoleMenuPO();
      relation.setRoleId(roleId);
      relation.setMenuId(menuId);
      roleMenuMapper.insert(relation);
    }
  }

  private Map<Long, MenuPO> menusByIds(
          Collection<Long> menuIds) {

    List<Long> normalized = normalizePositiveIds(menuIds);
    if (normalized.isEmpty()) {
      return Collections.emptyMap();
    }

    List<MenuPO> menus = menuMapper.selectBatchIds(normalized);
    if (CollectionUtils.isEmpty(menus)) {
      return Collections.emptyMap();
    }

    return menus.stream()
            .filter(menu -> menu.getId() != null)
            .collect(Collectors.toMap(
                    MenuPO::getId,
                    menu -> menu,
                    (left, right) -> left,
                    LinkedHashMap::new));
  }

  private Map<String, Long> activePermissionsByCode() {
    Map<String, Long> result = new HashMap<>();
    for (Permission permission
            : permissionDao.selectAllAndAscOrderByLevel()) {
      if (permission == null
              || permission.getId() == null
              || !Boolean.TRUE.equals(permission.getActive())
              || !StringUtils.hasText(
              permission.getPermissionCode())) {
        continue;
      }
      result.putIfAbsent(
              permission.getPermissionCode(),
              permission.getId());
    }
    return result;
  }

  private void addMenuAndParents(
          MenuPO menu,
          Map<String, MenuPO> menusByCode,
          Set<String> result) {

    Set<String> visited = new HashSet<>();
    MenuPO current = menu;
    while (current != null
            && StringUtils.hasText(current.getMenuCode())
            && visited.add(current.getMenuCode())) {
      result.add(current.getMenuCode());
      current = StringUtils.hasText(current.getParentCode())
              ? menusByCode.get(current.getParentCode())
              : null;
    }
  }

  private boolean isAvailable(MenuPO menu) {
    return menu != null
            && Boolean.TRUE.equals(menu.getActive())
            && Boolean.TRUE.equals(menu.getVisible());
  }

  private String buildDescription(MenuPO menu) {
    String description = menu.getDescription();
    if (!StringUtils.hasText(menu.getRoutePath())) {
      return description;
    }
    return StringUtils.hasText(description)
            ? description + "（" + menu.getRoutePath() + "）"
            : menu.getRoutePath();
  }

  private List<Long> normalizePositiveIds(
          Collection<Long> values) {

    if (CollectionUtils.isEmpty(values)) {
      return new ArrayList<>();
    }
    return values.stream()
            .filter(Objects::nonNull)
            .filter(value -> value > 0L)
            .distinct()
            .collect(Collectors.toList());
  }
}
