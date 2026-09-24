package io.yak.framework.security.service;

/**
 * Checks permissions granted to a user through roles.
 */
public interface RbacPermissionService {

  /**
   * Determine whether a user owns a permission.
   *
   * @param userName current user name
   * @param permissionCode required permission code
   * @return {@code true} when permission is granted
   */
  boolean hasPermission(String userName, String permissionCode);
}
