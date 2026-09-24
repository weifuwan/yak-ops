package io.yak.framework.security.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 在现有角色权限树协议中编码菜单节点。
 *
 * <p>真实权限 ID 为正数；菜单节点编码为 {@code -(menuId + 1)}；
 * {@code -1} 保留给“菜单权限”虚拟分组。这样旧前端仍可提交同一个
 * permissionIdList，而数据库继续分别保存权限和菜单关系。
 */
public final class MenuSelectionCodec {

  public static final long MENU_GROUP_NODE_ID = -1L;

  private MenuSelectionCodec() {
  }

  public static long encodeMenuId(Long menuId) {
    if (menuId == null || menuId <= 0L) {
      throw new IllegalArgumentException("菜单 ID 必须为正数");
    }
    return -(menuId + 1L);
  }

  public static boolean isEncodedMenuId(Long value) {
    return value != null && value < MENU_GROUP_NODE_ID;
  }

  public static long decodeMenuId(Long encodedId) {
    if (!isEncodedMenuId(encodedId)) {
      throw new IllegalArgumentException("不是有效的菜单选择 ID");
    }
    return -encodedId - 1L;
  }

  public static List<Long> extractPermissionIds(
          Collection<Long> values) {

    Set<Long> result = new LinkedHashSet<>();
    if (values != null) {
      for (Long value : values) {
        if (value != null && value > 0L) {
          result.add(value);
        }
      }
    }
    return new ArrayList<>(result);
  }

  public static List<Long> extractMenuIds(
          Collection<Long> values) {

    Set<Long> result = new LinkedHashSet<>();
    if (values != null) {
      for (Long value : values) {
        if (isEncodedMenuId(value)) {
          result.add(decodeMenuId(value));
        }
      }
    }
    return new ArrayList<>(result);
  }
}
