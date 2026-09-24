package io.yak.ops.boot.controller.security.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.common.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import io.yak.ops.common.bean.dto.security.account.AccountLoginDTO;
import io.yak.ops.common.enums.security.ResultCode;
import io.yak.ops.common.bean.vo.security.user.CurrentUserVO;
import io.yak.ops.common.bean.vo.security.user.UserBriefVO;
import io.yak.ops.common.exception.YakSecurityException;
import io.yak.ops.security.service.LoginService;
import io.yak.ops.security.service.UserService;
import io.yak.ops.security.web.PublicEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录相关接口。 */
@ConditionalOnProperty(
    prefix = "yak.security",
    name = {"enabled", "database-enabled", "web-enabled"},
    havingValue = "true",
    matchIfMissing = true)
@Tag(name = "账户认证")
@RestController
@RequestMapping("/yak-security/api/v1/account")
public class LoginController {

  private final LoginService loginService;
  private final UserService userService;
  private final AuthenticationManager
          authenticationManager;

  public LoginController(
          LoginService loginService,
          UserService userService,
          AuthenticationManager
                  authenticationManager) {
    this.loginService = loginService;
    this.userService = userService;
    this.authenticationManager =
            authenticationManager;
  }

  @Operation(summary = "用户登录")
  @PostMapping("/login")
  @PublicEndpoint
  public Result<UserBriefVO> login(
          HttpServletRequest request,
          HttpServletResponse response,
          @Valid @RequestBody
                  AccountLoginDTO loginDTO) {

    return Result.success(
            loginService.verifyLogin(
                    loginDTO,
                    request,
                    response));
  }

  @Operation(summary = "获取当前登录用户")
  @GetMapping("/current")
  public Result<CurrentUserVO> current() {
    if (!authenticationManager.isLogin()) {
      throw new YakSecurityException(
              ResultCode.USER_NOT_LOGIN);
    }

    String username =
            authenticationManager
                    .getLoginUsername();
    UserBriefVO brief =
            userService
                    .getUserBriefByUsername(
                            username);

    if (brief == null) {
      authenticationManager.logout();
      throw new YakSecurityException(
              ResultCode.USER_NOT_EXISTS);
    }

    CurrentUserVO user =
            new CurrentUserVO();
    user.setId(brief.getId());
    user.setUserName(
            brief.getUserName());
    user.setRealName(
            brief.getRealName());
    user.setDeptId(
            brief.getDeptId());
    user.setPhone(brief.getPhone());
    user.setEmail(brief.getEmail());

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
}
