package io.yak.ops.flow.connector.jdbc.source;

import io.yak.ops.flow.api.checkpoint.CheckpointState;

/**
 * JDBC Enumerator 的分片分配状态。
 *
 * @param nextSplitIndex 下一份待分配 split 的下标
 * @author weifuwan
 * @since 2026-09-27
 */
record JdbcEnumeratorState(int nextSplitIndex) implements CheckpointState {}
