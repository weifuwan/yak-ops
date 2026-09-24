package io.yak.ops.boot.controller.security.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.framework.security.common.dto.account.AccountLoginDTO;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.vo.user.CurrentUserVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.service.LoginService;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.web.PublicEndpoint;
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
    name = {"database-enabled", "web-enabled"},
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
