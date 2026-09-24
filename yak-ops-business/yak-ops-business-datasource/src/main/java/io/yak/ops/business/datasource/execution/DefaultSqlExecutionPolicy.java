package io.yak.ops.business.datasource.execution;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionPolicy;
import io.yak.ops.core.execution.sql.SqlExecutionPolicyViolationException;
import io.yak.ops.core.execution.sql.SqlStatementClassification;
import org.springframework.stereotype.Component;

/** Default caller-aware SQL policy for the shared execution runtime. */
@Component
@ConditionalOnDataSourceEnabled
public final class DefaultSqlExecutionPolicy implements SqlExecutionPolicy {

    @Override
    public void validate(SqlExecutionContext context, SqlStatementClassification classification) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (classification == null) {
            throw new IllegalArgumentException("classification must not be null");
        }

        SqlExecutionCaller caller = context.caller();
        if (classification.containsTransactionControl()) {
            throw violation(
                    caller, classification, "Transaction control SQL is runtime-owned; use SqlTransactionMode instead");
        }

        switch (caller) {
            case DATASET, DATA_SERVICE, ANALYSIS -> {
                if (!classification.readOnly()) {
                    throw violation(caller, classification, caller + " only allows strictly read-only SQL");
                }
            }
            case CONSOLE, SQL_TASK, SYSTEM -> {
                // These callers may execute write/DDL/vendor-specific SQL. Product-level confirmation,
                // authorization, and dangerous-SQL controls can layer on top without weakening the
                // read-only guarantee for data-consumption callers.
            }
        }
    }

    private static SqlExecutionPolicyViolationException violation(
            SqlExecutionCaller caller, SqlStatementClassification classification, String message) {
        return new SqlExecutionPolicyViolationException(caller, classification, message);
    }
}
