package io.yak.ops.boot.workspace;

import io.yak.ops.business.workspace.WorkspaceService;
import io.yak.ops.business.workspace.constant.WorkspaceConstants;
import io.yak.ops.business.workspace.enums.WorkspaceErrorCode;
import io.yak.ops.business.workspace.exception.WorkspaceException;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 校验 X-Workspace-Id 与当前用户成员关系，并管理请求范围 WorkspaceContext 生命周期。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
public class WorkspaceContextInterceptor implements HandlerInterceptor {

    private final AuthenticationManager authenticationManager;
    private final WorkspaceService workspaceService;

    public WorkspaceContextInterceptor(AuthenticationManager authenticationManager, WorkspaceService workspaceService) {
        this.authenticationManager = authenticationManager;
        this.workspaceService = workspaceService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        WorkspaceContext.clear();
        String workspaceId = StringUtils.trimToNull(request.getHeader(WorkspaceConstants.HEADER_NAME));
        if (workspaceId == null) return true;

        String userId = authenticationManager.getLoginUserId();
        if (StringUtils.isBlank(userId) || !workspaceService.isMember(workspaceId, userId)) {
            throw new WorkspaceException(WorkspaceErrorCode.ACCESS_DENIED);
        }

        WorkspaceContext.bind(workspaceId);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        WorkspaceContext.clear();
    }
}
