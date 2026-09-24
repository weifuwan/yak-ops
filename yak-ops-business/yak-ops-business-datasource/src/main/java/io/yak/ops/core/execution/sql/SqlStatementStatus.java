package io.yak.ops.core.execution.sql;

/** Lifecycle status of one statement inside a tracked SQL execution. */
public enum SqlStatementStatus {
  PENDING,
  RUNNING,
  SUCCEEDED,
  FAILED,
  CANCELLED,
  TIMED_OUT,
  SKIPPED;

  public boolean terminal() {
    return this != PENDING && this != RUNNING;
  }
}
