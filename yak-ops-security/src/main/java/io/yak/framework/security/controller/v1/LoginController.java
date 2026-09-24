package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.constant.Constants;
import io.yak.framework.security.common.dto.account.AccountLoginDTO;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.vo.user.CurrentUserVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.context.CurrentUser;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.service.LoginService;
import io.yak.framework.security.service.RoleService;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.service.impl.CurrentUserProjectResolver;
import io.yak.framework.security.service.impl.UserMenuGrantService;
import io.yak.framework.security.web.PublicEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录账户管理接口。
 *
 * @author weifuwan
 */
@Tag(name = Constants.SWAGGER_API_TAG_PREFIX + "账户认证接口")
@RestController
@RequestMapping("/yak-security/api/v1/account")
public class LoginController {

  private final LoginService loginService;
  private final UserService userService;
  private final RoleService roleService;
  private final CurrentUser currentUser;
  private final ObjectProvider<UserMenuGrantService>
          userMenuGrantServiceProvider;
  private final CurrentUserProjectResolver
          currentUserProjectResolver;

  public LoginController(
          LoginService loginService,
          UserService userService,
          RoleService roleService,
          CurrentUser currentUser,
          ObjectProvider<UserMenuGrantService>
                  userMenuGrantServiceProvider,
          CurrentUserProjectResolver
                  currentUserProjectResolver) {
    this.loginService = loginService;
    this.userService = userService;
    this.roleService = roleService;
    this.currentUser = currentUser;
    this.userMenuGrantServiceProvider =
            userMenuGrantServiceProvider;
    this.currentUserProjectResolver =
            currentUserProjectResolver;
  }

  @Operation(summary = "用户登录")
  @PostMapping("/login")
  @PublicEndpoint
  public Result<UserBriefVO> login(
          HttpServletRequest request,
          HttpServletResponse response,
          @Valid @RequestBody AccountLoginDTO loginDTO) {

    UserBriefVO currentUser =
            loginService.verifyLogin(
                    loginDTO,
                    request,
                    response);
    return Result.success(currentUser);
  }

  @Operation(summary = "获取当前登录用户")
  @GetMapping("/current")
  public Result<CurrentUserVO> current() {

    if (!currentUser.isAuthenticated()) {
      throw new YakSecurityException(
              ResultCode.USER_NOT_LOGIN);
    }

    UserBriefVO brief = userService.getUserBriefByUsername(
            currentUser.getUsername());
    if (brief == null) {
      throw new YakSecurityException(
              ResultCode.USER_NOT_EXISTS);
    }

    CurrentUserVO user = toCurrentUser(brief);
    user.setRoleList(
            roleService.getRoleBriefListByUserId(
                    user.getId()));
    if (user.getRoleList() == null) {
      user.setRoleList(new ArrayList<>());
    }

    user.setPermissionCodes(
            new ArrayList<>(
                    currentUser.getPermissionCodes()));
    user.setMenuCodes(
            new ArrayList<>(
                    currentUser.getMenuCodes()));
    user.setProjectList(
            currentUserProjectResolver.resolve(
                    user,
                    currentUser.getProjectIds()));

    return Result.success(user);
  }

  @Operation(summary = "用户退出登录")
  @PostMapping("/logout")
  public Result<Boolean> logout(
          HttpServletRequest request,
          HttpServletResponse response) {

    return loginService.logout(
            request,
            response);
  }

  private CurrentUserVO toCurrentUser(
          UserBriefVO brief) {
    CurrentUserVO user = new CurrentUserVO();
    user.setId(brief.getId());
    user.setUserName(brief.getUserName());
    user.setRealName(brief.getRealName());
    user.setDeptId(brief.getDeptId());
    user.setPhone(brief.getPhone());
    user.setEmail(brief.getEmail());
    return user;
  }
}
