package io.yak.framework.security.web;

import io.yak.framework.common.Result;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.service.LoginService;
import io.yak.framework.security.service.RbacPermissionService;
import io.yak.framework.security.util.JsonUtils;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Yak Security 统一登录认证拦截器。
 *
 * <p>默认保护所有 MVC 接口，仅放行配置的公开路径、标记了
 * {@link PublicEndpoint} 的接口以及浏览器 CORS 预检请求。</p>
 */
public class YakAuthenticationInterceptor implements HandlerInterceptor {

    private final LoginService loginService;
    private final YakSecurityProperties properties;
    private final RbacPermissionService permissionService;
    private final CurrentUserProvider currentUserProvider;

    public YakAuthenticationInterceptor(
            LoginService loginService,
            YakSecurityProperties properties,
            RbacPermissionService permissionService,
            CurrentUserProvider currentUserProvider) {
        this.loginService = loginService;
        this.properties = properties;
        this.permissionService = permissionService;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {
        // ERROR dispatch 只负责渲染已经失败的原始请求，不应重新建立认证边界。
        // 此时 Sa-Token 的请求上下文可能已清理，重复认证会覆盖真正的业务异常。
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            return true;
        }

        if (!properties.isAuthenticationEnabled()
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || isPublicEndpoint(handler)) {
            return true;
        }

        String contextPath = request.getContextPath();
        String requestPath = request.getRequestURI();
        if (contextPath != null && !contextPath.isEmpty()
                && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }

        boolean authenticated = loginService.interceptorCheck(
                request,
                response,
                requestPath,
                properties.getPublicPaths());
        if (!authenticated) {
            return false;
        }

        RequiresPermission required = findRequiredPermission(handler);
        if (required == null || permissionService.hasPermission(
                currentUserProvider.getCurrentUser(request), required.value())) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(JsonUtils.toJson(Result.fail(ResultCode.NO_PERMISSION)));
        return false;
    }

    private RequiresPermission findRequiredPermission(Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return null;
        }
        HandlerMethod method = (HandlerMethod) handler;
        RequiresPermission annotation = AnnotatedElementUtils.findMergedAnnotation(
                method.getMethod(), RequiresPermission.class);
        return annotation != null ? annotation : AnnotatedElementUtils.findMergedAnnotation(
                method.getBeanType(), RequiresPermission.class);
    }

    private boolean isPublicEndpoint(Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return false;
        }
        HandlerMethod method = (HandlerMethod) handler;
        return AnnotatedElementUtils.hasAnnotation(
                method.getMethod(), PublicEndpoint.class)
                || AnnotatedElementUtils.hasAnnotation(
                method.getBeanType(), PublicEndpoint.class);
    }
}
