package io.yak.ops.security.web;

import io.yak.ops.security.config.YakSecurityProperties;
import io.yak.ops.security.service.LoginService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Yak Security 登录拦截器。
 *
 * <p>当前只负责判断是否登录，不承载角色、权限或项目授权。</p>
 */
public class YakAuthenticationInterceptor
        implements HandlerInterceptor {

  private final LoginService loginService;
  private final YakSecurityProperties properties;

  public YakAuthenticationInterceptor(
          LoginService loginService,
          YakSecurityProperties properties) {
    this.loginService = loginService;
    this.properties = properties;
  }

  @Override
  public boolean preHandle(
          HttpServletRequest request,
          HttpServletResponse response,
          Object handler) throws Exception {

    if (request.getDispatcherType()
            == DispatcherType.ERROR) {
      return true;
    }

    if (!properties.isAuthenticationEnabled()
            || "OPTIONS".equalsIgnoreCase(
                    request.getMethod())
            || isPublicEndpoint(handler)) {
      return true;
    }

    String contextPath = request.getContextPath();
    String requestPath = request.getRequestURI();
    if (contextPath != null
            && !contextPath.isEmpty()
            && requestPath.startsWith(
                    contextPath)) {
      requestPath =
              requestPath.substring(
                      contextPath.length());
    }

    return loginService.interceptorCheck(
            request,
            response,
            requestPath,
            properties.getPublicPaths());
  }

  private boolean isPublicEndpoint(
          Object handler) {
    if (!(handler instanceof HandlerMethod method)) {
      return false;
    }

    return AnnotatedElementUtils.hasAnnotation(
            method.getMethod(),
            PublicEndpoint.class)
            || AnnotatedElementUtils.hasAnnotation(
                    method.getBeanType(),
                    PublicEndpoint.class);
  }
}
