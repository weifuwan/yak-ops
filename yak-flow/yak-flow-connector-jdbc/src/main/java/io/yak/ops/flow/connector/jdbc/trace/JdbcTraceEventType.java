package io.yak.ops.flow.connector.jdbc.trace;

/**
 * JDBC Connector 当前暴露的运行诊断事件类型。
 *
 * @author weifuwan
 * @since 2026-10-03
 */
public enum JdbcTraceEventType {
    SOURCE_SPLIT_PLANNED,
    SOURCE_SPLIT_STARTED,
    SOURCE_SPLIT_FINISHED,
    SOURCE_SPLIT_FAILED,
    SINK_OPENED,
    SINK_BATCH_COMMITTED,
    SINK_BATCH_FAILED
}
