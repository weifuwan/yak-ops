package io.yak.ops.core.execution.sql;

/** Basic synchronous timing retained while callers migrate to the shared runtime. */
public record SqlExecutionTiming(long openMillis, long executeMillis, long totalMillis) {

  public SqlExecutionTiming {
    if (openMillis < 0 || executeMillis < 0 || totalMillis < 0) {
      throw new IllegalArgumentException("SQL execution timings must not be negative");
    }
  }
}
