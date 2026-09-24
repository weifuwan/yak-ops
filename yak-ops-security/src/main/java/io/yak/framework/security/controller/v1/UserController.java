package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.constant.Constants;
import io.yak.framework.security.common.constant.SecurityPermissionCode;
import io.yak.framework.security.common.dto.user.UserDTO;
import io.yak.framework.security.common.dto.user.UserPasswordResetDTO;
import io.yak.framework.security.common.dto.user.UserQueryDTO;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.vo.role.AssignInfoVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.common.vo.user.UserVO;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.permission.YakPermission;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.service.impl.UserAdministrationService;
import io.yak.framework.security.util.HttpRequestUtil;
import io.yak.framework.security.util.JsonUtils;
import io.yak.framework.security.web.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口。
 *
 * @author weifuwan
 */
@Tag(name = Constants.SWAGGER_API_TAG_PREFIX + "用户管理接口")
@RestController
@RequestMapping("/yak-security/api/v1/user")
@RequiresPermission(SecurityPermissionCode.User.READ)
@YakPermission(
        code = SecurityPermissionCode.User.READ,
        name = "查看用户管理",
        group = SecurityPermissionCode.GROUP_NAME,
        groupCode = SecurityPermissionCode.GROUP_CODE,
        description = "查看用户列表、详情及角色分配信息")
public class UserController {

  private final UserService userService;
  private final UserAdministrationService userAdministrationService;

  public UserController(
          UserService userService,
          UserAdministrationService userAdministrationService) {

    this.userService = userService;
    this.userAdministrationService =
            userAdministrationService;
  }

  @Operation(summary = "校验用户字段是否可用")
  @GetMapping("/{type}/{value}/check")
  public Result<Void> check(
          @PathVariable Integer type,
          @PathVariable String value) {

    return userService.check(type, value);
  }

  @Operation(summary = "根据用户 ID 集合批量查询用户详情")
  @GetMapping
  public Result<List<UserVO>> detailList(
          @RequestParam("ids") String ids) {

    try {
      List<Long> userIds =
              JsonUtils.toList(ids, Long.class);

      return userService
              .getUserDetailsByUserIds(userIds);
    } catch (Exception exception) {
      throw new YakSecurityException(
              ResultCode.PARAM_NOT_VALID,
              exception);
    }
  }

  @Operation(summary = "根据用户 ID 查询用户详情")
  @GetMapping("/{id}")
  public Result<UserVO> detail(
          @PathVariable("id") Long userId) {

    return Result.success(
            userService.getUserDetailByUserId(
                    userId));
  }

  @Operation(summary = "分页查询用户")
  @PostMapping("/page")
  public Result<PagingData<UserVO>> page(
          @RequestBody UserQueryDTO queryDTO) {

    PagingData<UserVO> pagingData =
            userService.getUserPage(queryDTO);

    return Result.success(pagingData);
  }

  @Operation(summary = "根据部门 ID 查询用户")
  @GetMapping("/list/dept/{deptId}")
  public Result<List<UserBriefVO>> listByDeptId(
          @PathVariable Long deptId) {

    return Result.success(
            userService
                    .getUserBriefListByDeptId(
                            deptId));
  }

  @Operation(summary = "根据角色 ID 查询用户")
  @GetMapping("/list/role/{roleId}")
  public Result<List<UserBriefVO>> listByRoleId(
          @PathVariable Long roleId) {

    return Result.success(
            userService
                    .getUserBriefListByRoleId(
                            roleId));
  }

  @Operation(summary = "查询用户的角色分配信息")
  @GetMapping("/assign/list/{userId}")
  public Result<List<AssignInfoVO>> assignList(
          @PathVariable Long userId) {

    return Result.success(
            userService
                    .getAssignInfoListByUserId(
                            userId));
  }

  @Operation(summary = "根据用户名或真实姓名模糊查询用户")
  @GetMapping("/list/{keyword}")
  public Result<List<UserBriefVO>> listByName(
          @PathVariable String keyword) {

    return Result.success(
            userService.searchUserBriefList(
                    keyword));
  }

  /**
   * 新增用户。
   *
   * <p>保留原有 PUT 请求方式，避免影响现有前端调用。</p>
   */
  @Operation(summary = "新增用户")
  @PutMapping("/add")
  @RequiresPermission(SecurityPermissionCode.User.CREATE)
  @YakPermission(
          code = SecurityPermissionCode.User.CREATE,
          name = "新增用户",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          description = "创建系统用户")
  public Result<Void> add(
          HttpServletRequest request,
          @RequestBody UserDTO userDTO) {

    return userService.addUser(
            userDTO,
            HttpRequestUtil.getOperator(request));
  }

  @Operation(summary = "编辑用户")
  @PostMapping("/edit")
  @RequiresPermission(SecurityPermissionCode.User.UPDATE)
  @YakPermission(
          code = SecurityPermissionCode.User.UPDATE,
          name = "编辑用户",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          description = "编辑用户基本资料")
  public Result<Void> edit(
          HttpServletRequest request,
          @RequestBody UserDTO userDTO) {

    String operator = HttpRequestUtil.getOperator(request);
    Result<Void> result =
            userService.editUser(
                    userDTO,
                    operator);

    if (!result.failed()
            && userDTO != null
            && StringUtils.hasText(userDTO.getPw())) {
      userAdministrationService
              .invalidateSessionsAfterPasswordChange(
                      userDTO.getUserName(),
                      operator);
    }

    return result;
  }

  /**
   * 管理员重置用户密码。
   *
   * <p>该接口仅更新密码字段，不再通过完整用户编辑接口间接重置，
   * 避免覆盖并发发生的资料及角色变更。</p>
   */
  @Operation(summary = "管理员重置用户密码")
  @PutMapping("/{id}/password")
  @RequiresPermission(SecurityPermissionCode.User.RESET_PASSWORD)
  @YakPermission(
          code = SecurityPermissionCode.User.RESET_PASSWORD,
          name = "重置用户密码",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          description = "管理员重置指定用户密码")
  public Result<Void> resetPassword(
          HttpServletRequest request,
          @PathVariable("id") Long userId,
          @RequestBody
                  UserPasswordResetDTO resetDTO) {

    userAdministrationService.resetPassword(
            userId,
            resetDTO,
            HttpRequestUtil.getOperator(request));

    return Result.success();
  }

  @Operation(summary = "管理员强制下线用户")
  @PostMapping("/{id}/logout")
  @RequiresPermission(SecurityPermissionCode.User.UPDATE)
  public Result<Void> forceLogout(
          HttpServletRequest request,
          @PathVariable("id") Long userId) {

    userAdministrationService.forceLogout(
            userId,
            HttpRequestUtil.getOperator(request));

    return Result.success();
  }

  @Operation(summary = "根据用户 ID 删除用户")
  @DeleteMapping("/{id}")
  @RequiresPermission(SecurityPermissionCode.User.DELETE)
  @YakPermission(
          code = SecurityPermissionCode.User.DELETE,
          name = "删除用户",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          description = "删除系统用户并清理关联授权")
  public Result<Void> delete(
          HttpServletRequest request,
          @PathVariable("id") Long userId) {

    userAdministrationService.validateDelete(
            userId,
            HttpRequestUtil.getOperatorId(request),
            HttpRequestUtil.getOperator(request));

    return userService.deleteByUserId(userId);
  }
}
