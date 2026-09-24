package io.yak.ops.core.execution.sql;

import java.util.Objects;

/** Identifies the platform caller without leaking domain-specific models into the SQL runtime. */
public record SqlExecutionContext(
    SqlExecutionCaller caller,
    String callerReference,
    String operator) {

  public SqlExecutionContext {
    caller = Objects.requireNonNull(caller, "caller");
    callerReference = normalize(callerReference);
    operator = normalize(operator);
  }

  public static SqlExecutionContext of(SqlExecutionCaller caller, String callerReference) {
    return new SqlExecutionContext(caller, callerReference, null);
  }

  public static SqlExecutionContext of(
      SqlExecutionCaller caller,
      String callerReference,
      String operator) {
    return new SqlExecutionContext(caller, callerReference, operator);
  }

  public SqlExecutionContext withOperator(String resolvedOperator) {
    return new SqlExecutionContext(caller, callerReference, resolvedOperator);
  }

  public static SqlExecutionContext system() {
    return new SqlExecutionContext(SqlExecutionCaller.SYSTEM, null, null);
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }
}
