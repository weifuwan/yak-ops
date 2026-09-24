package io.yak.ops.boot.project;

import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.ops.core.project.ProjectAccessGuard;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import io.yak.ops.core.project.ProjectHeaders;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Establishes trusted CurrentProject only for endpoints that have entered Project Space rollout. */
@Component
public class ProjectScopeInterceptor implements HandlerInterceptor {

  private final ProjectContextRuntime currentProject;
  private final ProjectAccessGuard accessGuard;
  private final CurrentUserProvider currentUserProvider;

  public ProjectScopeInterceptor(
      ProjectContextRuntime currentProject,
      ProjectAccessGuard accessGuard,
      CurrentUserProvider currentUserProvider) {
    this.currentProject = currentProject;
    this.accessGuard = accessGuard;
    this.currentUserProvider = currentUserProvider;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    currentProject.clear();
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }

    ProjectMigrationMode mode = resolveMode(handlerMethod);
    if (mode == ProjectMigrationMode.LEGACY_GLOBAL) {
      return true;
    }

    String rawProjectId = request.getHeader(ProjectHeaders.PROJECT_ID);
    if (!StringUtils.hasText(rawProjectId) && mode == ProjectMigrationMode.PROJECT_OPTIONAL) {
      return true;
    }

    String username = currentUserProvider.getCurrentUser(request);
    if (!StringUtils.hasText(username)) {
      return true;
    }

    Long projectId = parseProjectId(rawProjectId, mode);
    if (projectId == null) {
      return true;
    }

    ProjectContext context = accessGuard.requireAccessible(projectId, username);
    currentProject.bind(context);
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception exception) {
    currentProject.clear();
  }

  ProjectMigrationMode resolveMode(HandlerMethod handlerMethod) {
    ProjectScope methodScope =
        AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ProjectScope.class);
    if (methodScope != null) {
      return methodScope.value();
    }

    ProjectScope typeScope =
        AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ProjectScope.class);
    return typeScope == null ? ProjectMigrationMode.LEGACY_GLOBAL : typeScope.value();
  }

  private Long parseProjectId(String rawProjectId, ProjectMigrationMode mode) {
    if (!StringUtils.hasText(rawProjectId)) {
      if (mode == ProjectMigrationMode.PROJECT_REQUIRED) {
        throw new ProjectContextException(ProjectContextError.PROJECT_REQUIRED);
      }
      return null;
    }

    try {
      long projectId = Long.parseLong(rawProjectId.trim());
      if (projectId <= 0) {
        throw new NumberFormatException("project ID must be positive");
      }
      return projectId;
    } catch (NumberFormatException exception) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND, exception);
    }
  }
}
