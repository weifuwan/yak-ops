package io.yak.ops.core.execution.sql;

/** Wraps checked datasource execution failures without exposing driver-specific contracts upstream. */
public final class SqlExecutionException extends RuntimeException {

  private final String dataSourceId;
  private final SqlExecutionContext context;

  public SqlExecutionException(
      String dataSourceId,
      SqlExecutionContext context,
      Throwable cause) {
    super(message(dataSourceId, context), cause);
    this.dataSourceId = dataSourceId;
    this.context = context;
  }

  public String dataSourceId() {
    return dataSourceId;
  }

  public SqlExecutionContext context() {
    return context;
  }

  private static String message(String dataSourceId, SqlExecutionContext context) {
    String caller = context == null ? "UNKNOWN" : context.caller().name();
    return "SQL execution failed for datasource " + dataSourceId + " (caller=" + caller + ")";
  }
}
