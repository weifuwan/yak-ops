package io.yak.ops.core.execution.sql;

/** Raised before datasource access when SQL violates the platform execution policy. */
public final class SqlExecutionPolicyViolationException extends RuntimeException {

  private final SqlExecutionCaller caller;
  private final SqlStatementClassification classification;

  public SqlExecutionPolicyViolationException(
      SqlExecutionCaller caller,
      SqlStatementClassification classification,
      String message) {
    super(message);
    this.caller = caller;
    this.classification = classification;
  }

  public SqlExecutionCaller caller() {
    return caller;
  }

  public SqlStatementClassification classification() {
    return classification;
  }
}
