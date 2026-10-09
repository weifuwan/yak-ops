package io.yak.ops.flow.connector.jdbc;

/**
 * JDBC Sink 当前支持的行级写入语义。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public enum JdbcWriteMode {

    /** 离线追加/覆盖后的批量 INSERT，只接受 INSERT RowKind。 */
    INSERT,

    /** 离线按目标主键执行数据库原生 UPSERT，只接受 INSERT RowKind。 */
    UPSERT,

    /** CDC changelog 写入，按主键顺序应用 INSERT/UPDATE/DELETE 并按批次提交事务。 */
    CHANGELOG
}
