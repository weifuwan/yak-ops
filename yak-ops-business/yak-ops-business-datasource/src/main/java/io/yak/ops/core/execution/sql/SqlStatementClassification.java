package io.yak.ops.core.execution.sql;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Conservative semantic classification for one SQL statement. */
public record SqlStatementClassification(
    SqlStatementType primaryType,
    Set<SqlStatementType> observedTypes) {

  public SqlStatementClassification {
    primaryType = Objects.requireNonNull(primaryType, "primaryType");
    EnumSet<SqlStatementType> copy = observedTypes == null || observedTypes.isEmpty()
        ? EnumSet.noneOf(SqlStatementType.class)
        : EnumSet.copyOf(observedTypes);
    if (primaryType != SqlStatementType.OTHER) copy.add(primaryType);
    observedTypes = Collections.unmodifiableSet(copy);
  }

  public static SqlStatementClassification other() {
    return new SqlStatementClassification(SqlStatementType.OTHER, Set.of());
  }

  /**
   * Strict read-only means both the primary statement and every observed nested semantic are
   * explicitly known to be read-only. This intentionally rejects EXPLAIN, CALL, SET, and unknown
   * vendor-specific syntax for read-only callers.
   */
  public boolean readOnly() {
    return primaryType.readOnlySafe()
        && !observedTypes.isEmpty()
        && observedTypes.stream().allMatch(SqlStatementType::readOnlySafe);
  }

  public boolean potentiallyMutating() {
    return observedTypes.stream().anyMatch(SqlStatementType::potentiallyMutating);
  }

  /** Any observed transaction control is rejected so it cannot bypass runtime transaction modes. */
  public boolean containsTransactionControl() {
    return observedTypes.stream().anyMatch(SqlStatementType::transactionControl);
  }
}
