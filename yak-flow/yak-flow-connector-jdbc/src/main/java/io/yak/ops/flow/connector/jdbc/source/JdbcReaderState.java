package io.yak.ops.flow.connector.jdbc.source;

import io.yak.ops.flow.api.checkpoint.CheckpointState;

/**
 * JDBC Reader 当前执行内的读取位置，仅用于 checkpoint 观察，不承诺跨进程一致恢复。
 *
 * @param rowsRead 当前 split 已读取行数
 * @author weifuwan
 * @since 2026-09-27
 */
record JdbcReaderState(long rowsRead) implements CheckpointState {}
