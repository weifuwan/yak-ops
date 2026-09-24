package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.framework.security.common.po.MenuPO;
import io.yak.framework.security.common.po.PermissionPO;
import io.yak.framework.security.common.vo.permission.PermissionTreeVO;
import io.yak.framework.security.dao.mapper.MenuMapper;
import io.yak.framework.security.dao.mapper.PermissionMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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
 * 维护菜单权限与按钮权限之间的包含关系。
 *
 * <p>菜单只代表页面访问能力，按钮权限代表页面内操作能力。按钮权限绑定菜单后，
 * 授权按钮会自动补齐所属菜单；只授权菜单不会反向授予任何按钮。</p>
 */
@Service("yakSecurityPermissionMenuRelationService")
public class PermissionMenuRelationService {

  public static final String NODE_TYPE_ROOT = "ROOT";
  public static final String NODE_TYPE_MENU_GROUP = "MENU_GROUP";
  public static final String NODE_TYPE_MENU = "MENU";
  public static final String NODE_TYPE_PERMISSION_GROUP = "PERMISSION_GROUP";
  public static final String NODE_TYPE_ACTION = "ACTION";

  private static final String MENU_PERMISSION_PREFIX = "menu:";

  private final PermissionMapper permissionMapper;
  private final MenuMapper menuMapper;

  public PermissionMenuRelationService(
      PermissionMapper permissionMapper,
      MenuMapper menuMapper) {
    this.permissionMapper = permissionMapper;
    this.menuMapper = menuMapper;
  }

  /** 根据已选按钮权限推导必须同时授予的菜单 ID。 */
  public List<Long> inferMenuIds(Collection<Long> permissionIds) {
    List<Long> normalized = normalizePositiveIds(permissionIds);
    if (normalized.isEmpty()) {
      return new ArrayList<>();
    }

    List<PermissionPO> permissions = permissionMapper.selectBatchIds(normalized);
    if (CollectionUtils.isEmpty(permissions)) {
      return new ArrayList<>();
    }

    List<MenuPO> menus = listActiveMenus();
    if (menus.isEmpty()) {
      return new ArrayList<>();
    }

    Map<String, MenuPO> menusByCode = menus.stream()
        .filter(menu -> StringUtils.hasText(menu.getMenuCode()))
        .collect(Collectors.toMap(
            MenuPO::getMenuCode,
            menu -> menu,
            (left, right) -> left,
            LinkedHashMap::new));
    Map<String, MenuPO> menusByRequiredPermission = menus.stream()
        .filter(menu -> StringUtils.hasText(menu.getRequiredPermissionCode()))
        .collect(Collectors.toMap(
            MenuPO::getRequiredPermissionCode,
            menu -> menu,
            (left, right) -> left,
            LinkedHashMap::new));

    Set<Long> result = new LinkedHashSet<>();
    for (PermissionPO permission : permissions) {
      if (permission == null || !Boolean.TRUE.equals(permission.getActive())) {
        continue;
      }
      MenuPO menu = resolveMenu(
          permission,
          menusByCode,
          menusByRequiredPermission);
      addMenuAndParents(menu, menusByCode, result);
    }
    return new ArrayList<>(result);
  }

  /** 将菜单和按钮整理成统一的“目录 → 菜单 → 按钮”能力树。 */
  public PermissionTreeVO mergeCapabilityTree(
      PermissionTreeVO permissionRoot,
      PermissionTreeVO menuTree,
      Collection<Long> selectedPermissionIds) {
    Set<Long> selected = new HashSet<>(
        normalizePositiveIds(selectedPermissionIds));
    PermissionTreeVO root = permissionRoot == null
        ? PermissionTreeVO.builder()
            .id(0L)
            .has(Boolean.FALSE)
            .leaf(Boolean.FALSE)
            .childList(new ArrayList<>())
            .build()
        : permissionRoot;
    root.setNodeType(NODE_TYPE_ROOT);
    root.setHas(Boolean.FALSE);

    PermissionTreeVO menus = menuTree == null
        ? PermissionTreeVO.builder()
            .id(MenuSelectionCodec.MENU_GROUP_NODE_ID)
            .permissionCode("menu")
            .permissionName("菜单与操作权限")
            .has(Boolean.FALSE)
            .leaf(Boolean.FALSE)
            .active(Boolean.TRUE)
            .childList(new ArrayList<>())
            .build()
        : menuTree;
    menus.setPermissionName("菜单与操作权限");
    menus.setDescription("菜单控制页面访问；按钮权限会自动包含所属菜单访问能力");
    menus.setNodeType(NODE_TYPE_MENU_GROUP);
    menus.setHas(Boolean.FALSE);

    Map<String, PermissionTreeVO> menuNodes = new LinkedHashMap<>();
    markMenuNodes(menus, menuNodes);

    Map<String, String> requiredPermissionByMenu = listActiveMenus().stream()
        .filter(menu -> StringUtils.hasText(menu.getMenuCode()))
        .filter(menu -> StringUtils.hasText(menu.getRequiredPermissionCode()))
        .collect(Collectors.toMap(
            MenuPO::getMenuCode,
            MenuPO::getRequiredPermissionCode,
            (left, right) -> left,
            LinkedHashMap::new));

    List<PermissionTreeVO> mappedActions = new ArrayList<>();
    detachMappedPermissions(root, mappedActions, true);
    for (PermissionTreeVO action : mappedActions) {
      if (!StringUtils.hasText(action.getMenuCode())) {
        continue;
      }
      PermissionTreeVO menu = menuNodes.get(action.getMenuCode());
      if (menu == null) {
        continue;
      }
      if (Objects.equals(
          action.getPermissionCode(),
          requiredPermissionByMenu.get(action.getMenuCode()))) {
        // 菜单节点本身已经代表页面读取权限，不重复显示“查看”按钮。
        continue;
      }
      action.setNodeType(NODE_TYPE_ACTION);
      action.setParentId(menu.getId());
      action.setLeaf(Boolean.TRUE);
      action.setChildList(null);
      action.setHas(action.getId() != null && selected.contains(action.getId()));
      if (menu.getChildList() == null) {
        menu.setChildList(new ArrayList<>());
      }
      menu.getChildList().add(action);
      menu.setLeaf(Boolean.FALSE);
    }

    markRemainingPermissionNodes(root, true, selected);
    if (root.getChildList() == null) {
      root.setChildList(new ArrayList<>());
    }
    root.getChildList().add(0, menus);
    root.setLeaf(Boolean.FALSE);
    return root;
  }

  private MenuPO resolveMenu(
      PermissionPO permission,
      Map<String, MenuPO> menusByCode,
      Map<String, MenuPO> menusByRequiredPermission) {
    if (StringUtils.hasText(permission.getMenuCode())) {
      MenuPO explicit = menusByCode.get(permission.getMenuCode());
      if (explicit != null) {
        return explicit;
      }
    }

    if (!StringUtils.hasText(permission.getPermissionCode())) {
      return null;
    }
    String permissionCode = permission.getPermissionCode();
    MenuPO exact = menusByRequiredPermission.get(permissionCode);
    if (exact != null) {
      return exact;
    }

    int separator = permissionCode.lastIndexOf(':');
    if (separator <= 0) {
      return null;
    }
    return menusByRequiredPermission.get(
        permissionCode.substring(0, separator) + ":read");
  }

  private void addMenuAndParents(
      MenuPO menu,
      Map<String, MenuPO> menusByCode,
      Set<Long> result) {
    Set<String> visited = new HashSet<>();
    MenuPO current = menu;
    while (current != null
        && current.getId() != null
        && Boolean.TRUE.equals(current.getActive())
        && StringUtils.hasText(current.getMenuCode())
        && visited.add(current.getMenuCode())) {
      result.add(current.getId());
      current = StringUtils.hasText(current.getParentCode())
          ? menusByCode.get(current.getParentCode())
          : null;
    }
  }

  private void markMenuNodes(
      PermissionTreeVO node,
      Map<String, PermissionTreeVO> menuNodes) {
    if (node == null) {
      return;
    }
    if (StringUtils.hasText(node.getPermissionCode())
        && node.getPermissionCode().startsWith(MENU_PERMISSION_PREFIX)) {
      String menuCode = node.getPermissionCode()
          .substring(MENU_PERMISSION_PREFIX.length());
      node.setMenuCode(menuCode);
      node.setNodeType(NODE_TYPE_MENU);
      menuNodes.put(menuCode, node);
    }
    if (node.getChildList() != null) {
      for (PermissionTreeVO child : node.getChildList()) {
        markMenuNodes(child, menuNodes);
      }
    }
  }

  private boolean detachMappedPermissions(
      PermissionTreeVO node,
      List<PermissionTreeVO> mapped,
      boolean root) {
    if (node == null) {
      return false;
    }
    List<PermissionTreeVO> children = node.getChildList();
    if (children != null) {
      Iterator<PermissionTreeVO> iterator = children.iterator();
      while (iterator.hasNext()) {
        PermissionTreeVO child = iterator.next();
        if (StringUtils.hasText(child.getMenuCode())) {
          mapped.add(child);
          iterator.remove();
        } else if (!detachMappedPermissions(child, mapped, false)) {
          iterator.remove();
        }
      }
    }
    return root
        || Boolean.TRUE.equals(node.getLeaf())
        || (node.getChildList() != null && !node.getChildList().isEmpty());
  }

  private void markRemainingPermissionNodes(
      PermissionTreeVO node,
      boolean root,
      Set<Long> selected) {
    if (node == null) {
      return;
    }
    if (!root) {
      boolean group = node.getChildList() != null
          && !node.getChildList().isEmpty();
      node.setNodeType(group
          ? NODE_TYPE_PERMISSION_GROUP
          : NODE_TYPE_ACTION);
      node.setHas(!group
          && node.getId() != null
          && selected.contains(node.getId()));
    }
    if (node.getChildList() != null) {
      for (PermissionTreeVO child : node.getChildList()) {
        markRemainingPermissionNodes(child, false, selected);
      }
    }
  }

  private List<MenuPO> listActiveMenus() {
    List<MenuPO> menus = menuMapper.selectList(
        Wrappers.<MenuPO>lambdaQuery()
            .eq(MenuPO::getActive, true)
            .orderByAsc(MenuPO::getSortOrder)
            .orderByAsc(MenuPO::getId));
    return menus == null ? Collections.emptyList() : menus;
  }

  private List<Long> normalizePositiveIds(Collection<Long> values) {
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
