package io.yak.framework.security.extend.impl;

import io.yak.framework.common.Result;
import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.ops.common.bean.dto.security.account.AccountLoginDTO;
import io.yak.framework.security.common.entity.user.User;
import io.yak.ops.common.enums.security.ResultCode;
import io.yak.ops.common.bean.vo.security.user.UserBriefVO;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.extend.LoginExtend;
import io.yak.framework.security.extend.PasswordEncoder;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/** 默认登录认证扩展。账号校验属于 Yak Security，登录态统一交给 AuthenticationManager。 */
public class DefaultLoginExtendImpl implements LoginExtend {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLoginExtendImpl.class);
  private static final Integer USER_DISABLED_STATUS = 2;
  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

  private final UserService userService;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final LoginAttemptGuard loginAttemptGuard;
  private final YakSecurityProperties.LoginSecurityProperties loginProperties;

  public DefaultLoginExtendImpl(
          UserService userService,
          PasswordEncoder passwordEncoder,
          YakSecurityProperties properties,
          AuthenticationManager authenticationManager) {
    this.userService = Objects.requireNonNull(userService, "userService must not be null");
    this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
    this.authenticationManager = Objects.requireNonNull(authenticationManager, "authenticationManager must not be null");
    Objects.requireNonNull(properties, "properties must not be null");
    this.loginProperties = properties.getLogin();
    this.loginAttemptGuard = new LoginAttemptGuard(loginProperties);
  }

  @Override
  public UserBriefVO verifyLogin(
          AccountLoginDTO loginDTO,
          HttpServletRequest request,
          HttpServletResponse response) throws YakSecurityException {
    validateLoginParam(loginDTO, request, response);
    String userName = loginDTO.getUserName().trim();
    String remoteAddress = request.getRemoteAddr();
    if (loginAttemptGuard.isBlocked(userName, remoteAddress)) {
      throw new YakSecurityException(ResultCode.USER_ACCOUNT_LOCKED);
    }

    User user = userService.getUserByUsername(userName);
    if (user == null) {
      loginAttemptGuard.recordFailure(userName, remoteAddress);
      throw new YakSecurityException(
              loginProperties.isHideAccountNotFound()
                      ? ResultCode.USER_CREDENTIALS_ERROR
                      : ResultCode.USER_NOT_EXISTS);
    }
    if (USER_DISABLED_STATUS.equals(user.getStatus())) {
      throw new YakSecurityException(ResultCode.USER_ACCOUNT_DISABLE);
    }
    if (!passwordEncoder.matches(loginDTO.getPw(), user.getPw())) {
      loginAttemptGuard.recordFailure(userName, remoteAddress);
      throw new YakSecurityException(ResultCode.USER_CREDENTIALS_ERROR);
    }
    if (user.getId() == null) {
      LOGGER.error("登录用户缺少用户 ID，userName={}", userName);
      throw new IllegalStateException("Login user id must not be null");
    }

    authenticationManager.login(user.getId(), userName);
    loginAttemptGuard.recordSuccess(userName, remoteAddress);
    return CopyBeanUtil.copy(user, UserBriefVO.class);
  }

  @Override
  public Result<Boolean> logout(
          HttpServletRequest request,
          HttpServletResponse response) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(response, "response must not be null");
    authenticationManager.logout();
    return Result.success(Boolean.TRUE);
  }

  @Override
  public boolean interceptorCheck(
          HttpServletRequest request,
          HttpServletResponse response,
          String requestPath,
          List<String> whiteListPatterns) throws IOException {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(response, "response must not be null");

    if (!StringUtils.hasText(requestPath)) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return false;
    }
    if (isWhiteListPath(requestPath, whiteListPatterns)) {
      return true;
    }
    if (!authenticationManager.isLogin()) {
      return handleUnauthorized(response);
    }

    Long loginUserId = authenticationManager.getLoginUserId();
    String operator = authenticationManager.getLoginUsername();
    if (loginUserId == null || !StringUtils.hasText(operator)) {
      authenticationManager.logout();
      return handleUnauthorized(response);
    }

    User user = userService.getUserByUsername(operator);
    if (user == null
            || USER_DISABLED_STATUS.equals(user.getStatus())
            || !Objects.equals(loginUserId, user.getId())) {
      LOGGER.warn("登录态失效，operator={}, loginUserId={}", operator, loginUserId);
      authenticationManager.logout();
      return handleUnauthorized(response);
    }
    return true;
  }

  private boolean handleUnauthorized(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json");
    response.getWriter().write(JsonUtils.toJson(Result.fail(ResultCode.USER_NOT_LOGIN)));
    return false;
  }

  private boolean isWhiteListPath(String requestPath, List<String> whiteListPatterns) {
    if (CollectionUtils.isEmpty(whiteListPatterns)) {
      return false;
    }
    for (String pattern : whiteListPatterns) {
      if (StringUtils.hasText(pattern) && PATH_MATCHER.match(pattern.trim(), requestPath)) {
        return true;
      }
    }
    return false;
  }

  private void validateLoginParam(
          AccountLoginDTO loginDTO,
          HttpServletRequest request,
          HttpServletResponse response) {
    if (loginDTO == null
            || request == null
            || response == null
            || !StringUtils.hasText(loginDTO.getUserName())
            || !StringUtils.hasText(loginDTO.getPw())) {
      throw new YakSecurityException(ResultCode.PARAM_NOT_VALID);
    }
  }
}
