package io.yak.ops.business.datasource.execution.audit;

import java.util.List;

/** Read-side detail projection for one SQL execution and its statements. */
public record SqlExecutionAuditDetail(SqlExecutionAuditRecord execution, List<SqlStatementAuditRecord> statements) {

    public SqlExecutionAuditDetail {
        if (execution == null) throw new IllegalArgumentException("execution must not be null");
        statements = statements == null ? List.of() : List.copyOf(statements);
    }
}
