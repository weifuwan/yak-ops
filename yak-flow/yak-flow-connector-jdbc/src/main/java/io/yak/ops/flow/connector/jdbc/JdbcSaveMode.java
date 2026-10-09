package io.yak.ops.flow.connector.jdbc;

/**
 * JDBC Sink 写入前对目标已有数据的处理方式。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
public enum JdbcSaveMode {

    /** 保留目标已有数据。 */
    APPEND,

    /** 写入前清空目标表已有数据。 */
    OVERWRITE
}
