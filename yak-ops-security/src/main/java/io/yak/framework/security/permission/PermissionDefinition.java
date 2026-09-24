package io.yak.framework.security.permission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/** A permission group and the permissions owned by it. */
public final class PermissionDefinition {
  private final String code;
  private final String name;
  private final List<Item> permissions;

  private PermissionDefinition(
      String code,
      String name,
      List<Item> permissions) {
    Assert.hasText(code, "Permission group code must not be blank");
    Assert.hasText(name, "Permission group name must not be blank");
    this.code = code.trim();
    this.name = name.trim();
    this.permissions = Collections.unmodifiableList(
        new ArrayList<>(permissions));
  }

  public static PermissionDefinition of(
      String code,
      String name,
      String... permissionCodes) {
    List<Item> items = new ArrayList<>();
    if (permissionCodes != null) {
      for (String permissionCode : permissionCodes) {
        items.add(Item.of(permissionCode, permissionCode));
      }
    }
    return new PermissionDefinition(code, name, items);
  }

  public static PermissionDefinition of(
      String code,
      String name,
      Item permission,
      Item... additionalPermissions) {
    Assert.notNull(permission, "Permission must not be null");
    List<Item> items = new ArrayList<>();
    items.add(permission);
    if (additionalPermissions != null) {
      Collections.addAll(items, additionalPermissions);
    }
    return new PermissionDefinition(code, name, items);
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public List<Item> getPermissions() {
    return permissions;
  }

  static PermissionDefinition fromItems(
      String code,
      String name,
      List<Item> permissions) {
    return new PermissionDefinition(code, name, permissions);
  }

  /** A leaf permission declaration. */
  public static final class Item {
    private final String code;
    private final String name;
    private final String description;
    private final String menuCode;

    private Item(
        String code,
        String name,
        String description,
        String menuCode) {
      Assert.hasText(code, "Permission code must not be blank");
      Assert.hasText(name, "Permission name must not be blank");
      this.code = code.trim();
      this.name = name.trim();
      this.description = StringUtils.hasText(description)
          ? description.trim()
          : null;
      this.menuCode = StringUtils.hasText(menuCode)
          ? menuCode.trim()
          : null;
    }

    public static Item of(String code, String name) {
      return new Item(code, name, null, null);
    }

    public static Item of(
        String code,
        String name,
        String description) {
      return new Item(code, name, description, null);
    }

    public static Item ofMenu(
        String code,
        String name,
        String description,
        String menuCode) {
      return new Item(code, name, description, menuCode);
    }

    public String getCode() {
      return code;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public String getMenuCode() {
      return menuCode;
    }
  }
}
