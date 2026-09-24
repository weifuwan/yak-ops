package io.yak.ops.common.constant.observability;

/** Stable API and permission constants for SQL execution observability. */
public final class SqlExecutionAuditConstants {

    public static final String API_PREFIX = "/api/v1/sql-executions";
    public static final String READ_PERMISSION = "resource:sql-execution:read";

    private SqlExecutionAuditConstants() {}
}
