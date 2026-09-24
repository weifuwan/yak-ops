package io.yak.ops.core.execution.sql;

/**
 * Observer for terminal SQL execution snapshots.
 *
 * <p>Observers are side-effect boundaries such as audit persistence or metrics export. Runtime
 * implementations must not let observer failures change the SQL execution outcome.
 */
@FunctionalInterface
public interface SqlExecutionObserver {

  void onExecutionCompleted(SqlExecutionSnapshot snapshot);
}
