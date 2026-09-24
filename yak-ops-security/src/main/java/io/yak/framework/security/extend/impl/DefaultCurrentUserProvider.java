package io.yak.framework.security.extend.impl;

import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.framework.security.extend.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;

/** 从统一认证边界读取当前登录用户名。 */
public class DefaultCurrentUserProvider implements CurrentUserProvider {

  private final AuthenticationManager authenticationManager;

  public DefaultCurrentUserProvider(AuthenticationManager authenticationManager) {
    this.authenticationManager = Objects.requireNonNull(
            authenticationManager,
            "authenticationManager must not be null");
  }

  @Override
  public String getCurrentUser(HttpServletRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    return authenticationManager.isLogin()
            ? authenticationManager.getLoginUsername()
            : null;
  }
}
