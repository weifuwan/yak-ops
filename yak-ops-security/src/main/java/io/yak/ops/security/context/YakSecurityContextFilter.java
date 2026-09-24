package io.yak.ops.security.context;

import io.yak.ops.security.authentication.AuthenticationManager;
import io.yak.ops.security.dao.UserRoleDao;
import io.yak.ops.security.service.impl.AuthorizationSnapshotService;
import io.yak.ops.security.util.HttpRequestUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** 从统一认证边界构建当前用户上下文。 */
public final class YakSecurityContextFilter extends OncePerRequestFilter {

  private final ObjectProvider<UserRoleDao> userRoleDaoProvider;
  private final ObjectProvider<AuthenticationManager> authenticationManagerProvider;
  private final ObjectProvider<AuthorizationSnapshotService>
          authorizationSnapshotServiceProvider;

  public YakSecurityContextFilter(
          ObjectProvider<UserRoleDao> userRoleDaoProvider,
          ObjectProvider<AuthenticationManager> authenticationManagerProvider) {
    this(
            userRoleDaoProvider,
            authenticationManagerProvider,
            null);
  }

  public YakSecurityContextFilter(
          ObjectProvider<UserRoleDao> userRoleDaoProvider,
          ObjectProvider<AuthenticationManager> authenticationManagerProvider,
          ObjectProvider<AuthorizationSnapshotService>
                  authorizationSnapshotServiceProvider) {
    this.userRoleDaoProvider = userRoleDaoProvider;
    this.authenticationManagerProvider = authenticationManagerProvider;
    this.authorizationSnapshotServiceProvider =
            authorizationSnapshotServiceProvider;
  }

  @Override
  protected void doFilterInternal(
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain) throws ServletException, IOException {
    YakSecurityContext.setCurrentUser(resolve(request));
    try {
      filterChain.doFilter(request, response);
    } finally {
      YakSecurityContext.clear();
    }
  }

  private CurrentUser resolve(HttpServletRequest request) {
    AuthenticationManager authenticationManager =
            authenticationManagerProvider == null
                    ? null
                    : authenticationManagerProvider.getIfAvailable();

    if (authenticationManager == null || !authenticationManager.isLogin()) {
      return buildCurrentUser(request, null, null);
    }

    return buildCurrentUser(
            request,
            authenticationManager.getLoginUserId(),
            authenticationManager.getLoginUsername());
  }

  private CurrentUser buildCurrentUser(
          HttpServletRequest request,
          Long userId,
          String username) {
    boolean authenticated = userId != null && StringUtils.hasText(username);
    AuthorizationSnapshot snapshot = authenticated
            ? findAuthorizationSnapshot(userId)
            : AuthorizationSnapshot.empty();
    return new YakSecurityContext.ImmutableCurrentUser(
            userId,
            username,
            HttpRequestUtil.getProjectId(request),
            snapshot,
            authenticated);
  }

  private AuthorizationSnapshot findAuthorizationSnapshot(
          Long userId) {
    AuthorizationSnapshotService snapshotService =
            authorizationSnapshotServiceProvider == null
                    ? null
                    : authorizationSnapshotServiceProvider.getIfAvailable();
    if (snapshotService != null) {
      return snapshotService.get(userId);
    }

    return AuthorizationSnapshot.forRoleIds(
            findRoleIds(userId));
  }

  private List<Long> findRoleIds(Long userId) {
    UserRoleDao userRoleDao = userRoleDaoProvider == null
            ? null
            : userRoleDaoProvider.getIfAvailable();
    if (userRoleDao == null) {
      return Collections.emptyList();
    }
    List<Long> roleIds = userRoleDao.selectRoleIdListByUserId(userId);
    return roleIds == null ? Collections.emptyList() : roleIds;
  }
}
