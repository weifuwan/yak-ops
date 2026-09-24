package io.yak.ops.security.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.security.common.po.PermissionPO;
import io.yak.ops.security.dao.mapper.PermissionMapper;
import io.yak.ops.security.exception.YakSecurityException;
import io.yak.ops.security.service.PermissionCache;
import io.yak.ops.security.service.RolePermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 权限管理补充服务。
 *
 * <p>在删除权限前执行服务端完整性校验，防止绕过前端限制删除声明式权限，
 * 或删除包含子节点的权限后留下孤立数据。
 *
 * @author weifuwan
 */
@Service
public class PermissionAdministrationService {

  private static final Long ROOT_PERMISSION_ID = 0L;

  private final PermissionMapper permissionMapper;
  private final RolePermissionService rolePermissionService;
  private final PermissionCache permissionCache;

  public PermissionAdministrationService(
          PermissionMapper permissionMapper,
          RolePermissionService rolePermissionService,
          PermissionCache permissionCache) {

    this.permissionMapper = permissionMapper;
    this.rolePermissionService = rolePermissionService;
    this.permissionCache = permissionCache;
  }

  /**
   * 删除手工权限及其角色关联。
   *
   * @param permissionId 权限 ID
   */
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void deletePermission(Long permissionId) {
    if (permissionId == null
            || Objects.equals(
            ROOT_PERMISSION_ID,
            permissionId)) {

      throw new YakSecurityException(
              "权限 ID 不正确");
    }

    PermissionPO permission =
            permissionMapper.selectById(permissionId);

    if (permission == null) {
      throw new YakSecurityException(
              "权限不存在");
    }

    if (Boolean.TRUE.equals(
            permission.getDeclared())) {

      throw new YakSecurityException(
              "声明式权限由后端注册维护，不能手工删除");
    }

    Long childCount =
            permissionMapper.selectCount(
                    Wrappers.<PermissionPO>lambdaQuery()
                            .eq(
                                    PermissionPO::getParentId,
                                    permissionId));

    if (childCount != null && childCount > 0) {
      throw new YakSecurityException(
              "该权限包含子权限，不能删除");
    }

    rolePermissionService
            .deleteRolePermissionByPermissionId(
                    permissionId);

    if (permissionMapper.deleteById(permissionId) != 1) {
      throw new YakSecurityException(
              "权限删除失败");
    }

    permissionCache.invalidateAll();
  }
}
