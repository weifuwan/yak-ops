package io.yak.ops.core.execution.sql;

/** Product/runtime origin that initiated a SQL execution. */
public enum SqlExecutionCaller {
  CONSOLE,
  SQL_TASK,
  DATASET,
  DATA_SERVICE,
  ANALYSIS,
  SYSTEM,
  TASK_PLUGIN
}
