package io.yak.ops.common.bean.vo.observability;

import java.util.List;

/** Execution detail including ordered statement metadata. */
public record SqlExecutionAuditDetailVO(SqlExecutionAuditVO execution, List<SqlStatementExecutionAuditVO> statements) {

    public SqlExecutionAuditDetailVO {
        statements = statements == null ? List.of() : List.copyOf(statements);
    }
}
