package io.yak.ops.business.workspace.constant;

import io.yak.ops.common.constant.CommonConstants;

/**
 * Workspace 与 Boot 共享的稳定协议常量。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
public final class WorkspaceConstants {

    /** Workspace HTTP API 根路径。 */
    public static final String API_PREFIX = CommonConstants.API_PREFIX + "/workspaces";

    /** 客户端声明当前 Workspace 的请求头。 */
    public static final String HEADER_NAME = "X-Workspace-Id";

    private WorkspaceConstants() {}
}
