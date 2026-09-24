package io.yak.ops.core.execution.sql;

/** Classifies SQL without depending on datasource-specific driver implementations. */
@FunctionalInterface
public interface SqlStatementClassifier {

  SqlStatementClassification classify(String sql);
}
