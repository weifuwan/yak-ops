package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.constant.Constants;
import io.yak.framework.security.common.constant.SecurityPermissionCode;
import io.yak.framework.security.common.dto.permission.PermissionDTO;
import io.yak.framework.security.common.vo.permission.PermissionTreeVO;
import io.yak.framework.security.permission.YakPermission;
import io.yak.framework.security.service.PermissionService;
import io.yak.framework.security.service.impl.PermissionAdministrationService;
import io.yak.framework.security.web.RequiresPermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限管理接口。
 *
 * @author weifuwan
 */
@Tag(name = Constants.SWAGGER_API_TAG_PREFIX + "权限管理接口")
@RestController
@RequestMapping("/yak-security/api/v1/permission")
@RequiresPermission(SecurityPermissionCode.Permission.READ)
@YakPermission(
        code = SecurityPermissionCode.Permission.READ,
        name = "查看权限管理",
        group = SecurityPermissionCode.GROUP_NAME,
        groupCode = SecurityPermissionCode.GROUP_CODE,
        menuCode = SecurityPermissionCode.Permission.MENU_CODE,
        description = "查看权限目录及权限树")
public class PermissionController {

  private final PermissionService permissionService;
  private final PermissionAdministrationService
          permissionAdministrationService;

  public PermissionController(
          PermissionService permissionService,
          PermissionAdministrationService
                  permissionAdministrationService) {

    this.permissionService = permissionService;
    this.permissionAdministrationService =
            permissionAdministrationService;
  }

  @Operation(summary = "查询完整权限树")
  @GetMapping("/tree")
  public Result<PermissionTreeVO> tree() {
    return Result.success(
            permissionService
                    .buildPermissionTree());
  }

  @Operation(summary = "导入权限树")
  @PostMapping("/import")
  @RequiresPermission(SecurityPermissionCode.Permission.IMPORT)
  @YakPermission(
          code = SecurityPermissionCode.Permission.IMPORT,
          name = "导入权限",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          menuCode = SecurityPermissionCode.Permission.MENU_CODE,
          description = "导入手工维护的权限目录")
  public Result<Void> importPermission(
          @RequestBody
                  List<PermissionDTO> permissionDTOList) {

    permissionService.savePermission(
            permissionDTOList);

    return Result.success(null);
  }

  /**
   * 删除手工权限及其角色关联。
   *
   * <p>服务端会拒绝声明式权限以及包含子节点的权限，
   * 防止绕过前端限制破坏权限树。
   */
  @Operation(summary = "删除手工权限及其角色关联")
  @DeleteMapping("/{permissionId}")
  @RequiresPermission(SecurityPermissionCode.Permission.DELETE)
  @YakPermission(
          code = SecurityPermissionCode.Permission.DELETE,
          name = "删除权限",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          menuCode = SecurityPermissionCode.Permission.MENU_CODE,
          description = "删除手工权限并解除角色授权")
  public Result<Void> deletePermission(
          @PathVariable Long permissionId) {

    permissionAdministrationService
            .deletePermission(permissionId);

    return Result.success(null);
  }
}
