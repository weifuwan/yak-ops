package io.yak.ops.flow.connector.jdbc.trace;

/**
 * JDBC Runtime Trace 失败发生的执行阶段。
 *
 * @author weifuwan
 * @since 2026-10-03
 */
public enum JdbcTraceFailureStage {
    SOURCE_OPEN,
    SOURCE_READ,
    SINK_WRITE,
    SINK_COMMIT
}
