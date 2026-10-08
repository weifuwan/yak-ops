package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;

/**
 * 一次已冻结 Route Attempt 运行需要的身份、快照和取消令牌。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
record RouteAttemptRuntimeContext(
        String workspaceId,
        String tableExecutionId,
        String attemptId,
        int attemptNo,
        DataSyncDefinitionSnapshotVO snapshot,
        MultiTableRunControl control) {}
