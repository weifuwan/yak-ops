package io.yak.ops.common.context;

import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.StringUtils;

/**
 * 保存当前请求已经校验过的 Workspace 标识，供 Workspace-scoped 业务能力读取。
 *
 * <p>Context 只保存请求范围状态，不负责认证或成员关系判断；绑定与清理由 Boot Web Runtime 负责。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
public final class WorkspaceContext {

    private static final ThreadLocal<String> CURRENT_WORKSPACE = new ThreadLocal<>();

    private WorkspaceContext() {}

    public static void bind(String workspaceId) {
        String normalized = StringUtils.trimToNull(workspaceId);
        if (normalized == null) throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        CURRENT_WORKSPACE.set(normalized);
    }

    public static String getWorkspaceId() {
        return CURRENT_WORKSPACE.get();
    }

    public static String requireWorkspaceId() {
        String workspaceId = CURRENT_WORKSPACE.get();
        if (StringUtils.isBlank(workspaceId)) throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        return workspaceId;
    }

    public static void clear() {
        CURRENT_WORKSPACE.remove();
    }
}
