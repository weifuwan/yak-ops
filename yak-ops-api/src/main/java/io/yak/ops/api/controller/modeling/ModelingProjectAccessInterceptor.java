package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.service.ProjectService;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.context.ProjectCodeContext;
import io.yak.ops.dao.entity.User;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Validates project access and activates workspace-level SQL isolation for
 * every modeling endpoint.
 */
public class ModelingProjectAccessInterceptor implements HandlerInterceptor {

    private final ProjectService projectService;

    public ModelingProjectAccessInterceptor(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        ProjectCodeContext.clear();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(variables instanceof Map)) {
            return true;
        }

        String rawProjectCode = ((Map<String, String>) variables).get("projectCode");
        if (rawProjectCode == null) {
            return true;
        }

        long projectCode = Long.parseLong(rawProjectCode);
        Object userAttribute = request.getAttribute(Constants.SESSION_USER);
        if (!(userAttribute instanceof User)) {
            throw new IllegalStateException("Authenticated user is required for data modeling");
        }

        User loginUser = (User) userAttribute;
        String method = request.getMethod();
        String requestPath = request.getRequestURI();
        boolean readOnlyPost = "POST".equalsIgnoreCase(method)
                && (requestPath.endsWith("/page")
                        || requestPath.endsWith("/tree")
                        || requestPath.endsWith("/all")
                        || requestPath.endsWith("/option"));
        boolean writeOperation = "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)
                || requestPath.contains("/loadall/related-domain/")
                || ("POST".equalsIgnoreCase(method) && !readOnlyPost);

        if (writeOperation) {
            projectService.checkHasProjectWritePermissionThrowException(loginUser, projectCode);
        } else {
            projectService.checkProjectAndAuthThrowException(loginUser, projectCode, null);
        }

        ProjectCodeContext.setProjectCode(projectCode);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        ProjectCodeContext.clear();
    }
}
