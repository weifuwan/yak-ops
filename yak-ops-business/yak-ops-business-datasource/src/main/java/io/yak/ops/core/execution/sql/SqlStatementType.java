package io.yak.ops.core.execution.sql;

/** Semantic SQL statement type used by execution policy and observability. */
public enum SqlStatementType {
  SELECT(true, false, false),
  VALUES(true, false, false),
  SHOW(true, false, false),
  DESCRIBE(true, false, false),

  INSERT(false, true, false),
  UPDATE(false, true, false),
  DELETE(false, true, false),
  MERGE(false, true, false),
  REPLACE(false, true, false),

  CREATE(false, true, false),
  ALTER(false, true, false),
  DROP(false, true, false),
  TRUNCATE(false, true, false),
  GRANT(false, true, false),
  REVOKE(false, true, false),

  CALL(false, true, false),
  EXPLAIN(false, false, false),
  SET(false, false, false),

  BEGIN(false, false, true),
  COMMIT(false, false, true),
  ROLLBACK(false, false, true),

  OTHER(false, false, false);

  private final boolean readOnlySafe;
  private final boolean potentiallyMutating;
  private final boolean transactionControl;

  SqlStatementType(
      boolean readOnlySafe,
      boolean potentiallyMutating,
      boolean transactionControl) {
    this.readOnlySafe = readOnlySafe;
    this.potentiallyMutating = potentiallyMutating;
    this.transactionControl = transactionControl;
  }

  /** True only for statement kinds that are safe for strict read-only product callers. */
  public boolean readOnlySafe() {
    return readOnlySafe;
  }

  /** True when the statement can modify data, schema, privileges, or invoke mutating procedures. */
  public boolean potentiallyMutating() {
    return potentiallyMutating;
  }

  /** Transaction control is owned by the runtime, not embedded SQL text. */
  public boolean transactionControl() {
    return transactionControl;
  }
}
