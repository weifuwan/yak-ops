package io.yak.ops.core.execution.sql;

/** Policy boundary that decides whether a classified SQL statement may execute for a caller. */
@FunctionalInterface
public interface SqlExecutionPolicy {

  void validate(SqlExecutionContext context, SqlStatementClassification classification);
}
