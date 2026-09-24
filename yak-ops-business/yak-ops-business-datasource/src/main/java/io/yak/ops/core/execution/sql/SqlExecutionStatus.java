package io.yak.ops.core.execution.sql;

/** Lifecycle status of one tracked SQL execution. */
public enum SqlExecutionStatus {
  PENDING,
  RUNNING,
  CANCELLING,
  SUCCEEDED,
  FAILED,
  CANCELLED,
  TIMED_OUT;

  public boolean terminal() {
    return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == TIMED_OUT;
  }
}
