package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.constant.Constants;
import io.yak.framework.security.common.constant.SecurityPermissionCode;
import io.yak.framework.security.common.dto.role.RoleAssignDTO;
import io.yak.framework.security.common.dto.role.RoleQueryDTO;
import io.yak.framework.security.common.dto.role.RoleSaveDTO;
import io.yak.framework.security.common.vo.role.AssignInfoVO;
import io.yak.framework.security.common.vo.role.RoleBriefVO;
import io.yak.framework.security.common.vo.role.RoleDeleteCheckVO;
import io.yak.framework.security.common.vo.role.RoleVO;
import io.yak.framework.security.permission.YakPermission;
import io.yak.framework.security.service.RoleService;
import io.yak.framework.security.util.HttpRequestUtil;
import io.yak.framework.security.web.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色管理接口。
 *
 * @author weifuwan
 */
@Tag(name = Constants.SWAGGER_API_TAG_PREFIX + "角色管理接口")
@RestController
@RequestMapping("/yak-security/api/v1/role")
@RequiresPermission(SecurityPermissionCode.Role.READ)
@YakPermission(
        code = SecurityPermissionCode.Role.READ,
        name = "查看角色管理",
        group = SecurityPermissionCode.GROUP_NAME,
        groupCode = SecurityPermissionCode.GROUP_CODE,
        description = "查看角色列表、详情及用户分配信息")
public class RoleController {

  private final RoleService roleService;

  /**
   * 创建角色管理接口。
   *
   * @param roleService 角色服务
   */
  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  /**
   * 根据角色 ID 查询角色详情。
   *
   * @param roleId 角色 ID
   * @return 角色详情
   */
  @Operation(summary = "根据角色 ID 查询角色详情")
  @GetMapping("/{id}")
  public Result<RoleVO> detail(
          @PathVariable("id") Long roleId) {

    return Result.success(
            roleService.getRoleDetailByRoleId(
                    roleId));
  }

  /**
   * 更新角色。
   *
   * @param request HTTP 请求
   * @param roleSaveDTO 角色信息
   * @return 更新结果
   */
  @Operation(summary = "更新角色")
  @PutMapping
  @RequiresPermission(SecurityPermissionCode.Role.UPDATE)
  @YakPermission(
          code = SecurityPermissionCode.Role.UPDATE,
          name = "编辑角色",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          description = "编辑角色资料及角色权限")
  public Result<Void> update(
          HttpServletRequest request,
          @RequestBody RoleSaveDTO roleSaveDTO) {

    roleService.updateRole(
            roleSaveDTO,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);

  }

  /**
   * 创建角色。
   *
   * @param request HTTP 请求
   * @param roleSaveDTO 角色信息
   * @return 创建结果
   */
  @Operation(summary = "创建角色")
  @PostMapping
  @RequiresPermission(SecurityPermissionCode.Role.CREATE)
  @YakPermission(
          code = SecurityPermissionCode.Role.CREATE,
          name = "新增角色",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          description = "创建角色并配置角色权限")
  public Result<Void> create(
          HttpServletRequest request,
          @RequestBody RoleSaveDTO roleSaveDTO) {

    roleService.createRole(
            roleSaveDTO,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);

  }

  /**
   * 执行角色删除前校验。
   *
   * <p>保留原有 DELETE 请求方式，避免影响现有前端调用。
   *
   * @param roleId 角色 ID
   * @return 删除校验结果
   */
  @Operation(summary = "执行角色删除前校验")
  @DeleteMapping("/delete/check/{id}")
  @RequiresPermission(SecurityPermissionCode.Role.DELETE)
  public Result<RoleDeleteCheckVO> check(
          @PathVariable("id") Long roleId) {

    return Result.success(
            roleService.checkBeforeDelete(
                    roleId));
  }

  /**
   * 从角色中删除用户。
   *
   * @param request HTTP 请求
   * @param roleId 角色 ID
   * @param userId 用户 ID
   * @return 删除结果
   */
  @Operation(summary = "从角色中删除用户")
  @DeleteMapping("/{id}/user/{userId}")
  @RequiresPermission(SecurityPermissionCode.Role.ASSIGN)
  public Result<Void> deleteUser(
          HttpServletRequest request,
          @PathVariable("id") Long roleId,
          @PathVariable Long userId) {

    roleService.deleteUserFromRole(
            roleId,
            userId,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);

  }

  /**
   * 根据角色 ID 删除角色。
   *
   * @param request HTTP 请求
   * @param roleId 角色 ID
   * @return 删除结果
   */
  @Operation(summary = "根据角色 ID 删除角色")
  @DeleteMapping("/{id}")
  @RequiresPermission(SecurityPermissionCode.Role.DELETE)
  @YakPermission(
          code = SecurityPermissionCode.Role.DELETE,
          name = "删除角色",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          description = "删除角色并解除相关授权")
  public Result<Void> delete(
          HttpServletRequest request,
          @PathVariable("id") Long roleId) {

    roleService.deleteRoleByRoleId(
            roleId,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);

  }

  /**
   * 分页查询角色。
   *
   * @param queryDTO 查询条件
   * @return 角色分页结果
   */
  @Operation(summary = "分页查询角色")
  @PostMapping("/page")
  public Result<PagingData<RoleVO>> page(
          @RequestBody RoleQueryDTO queryDTO) {

    PagingData<RoleVO> pagingData =
            roleService.getRolePage(queryDTO);

    return Result.success(pagingData);
  }

  /**
   * 分配角色或为角色分配用户。
   *
   * @param request HTTP 请求
   * @param assignDTO 分配参数
   * @return 分配结果
   */
  @Operation(summary = "分配角色或为角色分配用户")
  @PostMapping("/assign")
  @RequiresPermission(SecurityPermissionCode.Role.ASSIGN)
  @YakPermission(
          code = SecurityPermissionCode.Role.ASSIGN,
          name = "分配用户角色",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          description = "为用户分配角色或为角色分配用户")
  public Result<Void> assign(
          HttpServletRequest request,
          @RequestBody RoleAssignDTO assignDTO) {

    roleService.assignRoles(
            assignDTO,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);

  }

  /**
   * 根据角色 ID 查询用户分配信息。
   *
   * @param roleId 角色 ID
   * @return 用户分配信息列表
   */
  @Operation(summary = "根据角色 ID 查询用户分配信息")
  @GetMapping("/assign/list/{roleId}")
  public Result<List<AssignInfoVO>> assignList(
          @PathVariable Long roleId) {

    return Result.success(
            roleService.getAssignInfoByRoleId(
                    roleId));
  }

  /**
   * 根据角色名称查询角色。
   *
   * @param roleName 角色名称
   * @return 角色简要信息列表
   */
  @Operation(summary = "根据角色名称查询角色")
  @GetMapping({"/list/{roleName}", "/list"})
  public Result<List<RoleBriefVO>> list(
          @PathVariable(required = false)
                  String roleName) {

    return Result.success(
            roleService
                    .getRoleBriefListByRoleName(
                            roleName));
  }
}
