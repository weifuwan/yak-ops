package io.yak.ops.business.workspace.exception;

import io.yak.ops.business.workspace.enums.WorkspaceErrorCode;
import io.yak.ops.common.exception.BusinessException;

/**
 * Workspace 领域业务异常。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
public class WorkspaceException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public WorkspaceException(WorkspaceErrorCode errorCode) {
        super(errorCode);
    }
}
