package io.yak.ops.core.execution.sql;

/** Transaction boundary for a tracked SQL execution plan. */
public enum SqlTransactionMode {
  /** Each statement uses the datasource executor's normal auto-commit behavior. */
  AUTO_COMMIT,

  /** All statements execute on one datasource executor transaction and commit atomically. */
  SINGLE_TRANSACTION
}
