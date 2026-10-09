package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;

/**
 * 一次多表 Root 执行所持有的冻结定义、取消令牌与当前表指标镜像。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
record MultiTableRootContext(
        String workspaceId,
        String executionId,
        DataSyncDefinitionSnapshotVO snapshot,
        MultiTableRunControl control,
        MultiTableRootMetrics metrics) {}
