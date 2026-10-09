package io.yak.ops.flow.connector.cdc.mysql.source;

import io.yak.ops.flow.api.checkpoint.CheckpointState;

/**
 * MySQL CDC 单分片 Enumerator 状态。
 *
 * @param assigned CDC split 是否已经分配
 * @author weifuwan
 * @since 2026-09-27
 */
record MySqlCdcEnumeratorState(boolean assigned) implements CheckpointState {}
