package io.yak.ops.boot.controller.security.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.framework.security.common.dto.user.UserDTO;
import io.yak.framework.security.common.dto.user.UserPasswordResetDTO;
import io.yak.framework.security.common.dto.user.UserQueryDTO;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.common.vo.user.UserVO;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.service.impl.UserAdministrationService;
import io.yak.framework.security.util.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户管理接口。 */
@ConditionalOnProperty(
    prefix = "yak.security",
    name = {"database-enabled", "web-enabled"},
    havingValue = "true",
    matchIfMissing = true)
@Tag(name = "用户管理")
@RestController
@RequestMapping("/yak-security/api/v1/user")
public class UserController {

  private final UserService userService;
  private final UserAdministrationService
          userAdministrationService;
  private final AuthenticationManager
          authenticationManager;

  public UserController(
          UserService userService,
          UserAdministrationService
                  userAdministrationService,
          AuthenticationManager
                  authenticationManager) {
    this.userService = userService;
    this.userAdministrationService =
            userAdministrationService;
    this.authenticationManager =
            authenticationManager;
  }

  @Operation(summary = "校验用户字段是否可用")
  @GetMapping("/{type}/{value}/check")
  public Result<Void> check(
          @PathVariable Integer type,
          @PathVariable String value) {
    return userService.check(
            type,
            value);
  }

  @Operation(summary = "根据用户 ID 集合批量查询用户详情")
  @GetMapping
  public Result<List<UserVO>> detailList(
          @RequestParam("ids") String ids) {
    try {
      return userService
              .getUserDetailsByUserIds(
                      JsonUtils.toList(
                              ids,
                              Long.class));
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
            userService
                    .getUserDetailByUserId(
                            userId));
  }

  @Operation(summary = "分页查询用户")
  @PostMapping("/page")
  public Result<PagingData<UserVO>> page(
          @RequestBody
                  UserQueryDTO queryDTO) {
    return Result.success(
            userService
                    .getUserPage(queryDTO));
  }

  @Operation(summary = "根据用户名或真实姓名模糊查询用户")
  @GetMapping("/list/{keyword}")
  public Result<List<UserBriefVO>> listByName(
          @PathVariable String keyword) {
    return Result.success(
            userService
                    .searchUserBriefList(
                            keyword));
  }

  @Operation(summary = "新增用户")
  @PutMapping("/add")
  public Result<Void> add(
          HttpServletRequest request,
          @RequestBody UserDTO userDTO) {
    return userService.addUser(
            userDTO,
            currentUsername());
  }

  @Operation(summary = "编辑用户")
  @PostMapping("/edit")
  public Result<Void> edit(
          HttpServletRequest request,
          @RequestBody UserDTO userDTO) {

    String operator = currentUsername();
    Result<Void> result =
            userService.editUser(
                    userDTO,
                    operator);

    if (!result.failed()
            && userDTO != null
            && StringUtils.hasText(
                    userDTO.getPw())) {
      userAdministrationService
              .invalidateSessionsAfterPasswordChange(
                      userDTO.getUserName(),
                      operator);
    }

    return result;
  }

  @Operation(summary = "管理员重置用户密码")
  @PutMapping("/{id}/password")
  public Result<Void> resetPassword(
          @PathVariable("id") Long userId,
          @RequestBody
                  UserPasswordResetDTO resetDTO) {

    userAdministrationService.resetPassword(
            userId,
            resetDTO,
            currentUsername());

    return Result.success();
  }

  @Operation(summary = "根据用户 ID 删除用户")
  @DeleteMapping("/{id}")
  public Result<Void> delete(
          @PathVariable("id") Long userId) {

    userAdministrationService
            .validateDelete(
                    userId,
                    authenticationManager
                            .getLoginUserId(),
                    currentUsername());

    return userService.deleteByUserId(
            userId);
  }

  private String currentUsername() {
    return authenticationManager
            .getLoginUsername();
  }
}
