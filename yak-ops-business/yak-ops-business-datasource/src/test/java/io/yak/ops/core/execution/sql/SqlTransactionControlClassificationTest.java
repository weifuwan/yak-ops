package io.yak.ops.core.execution.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SqlTransactionControlClassificationTest {

  @Test
  void detectsTransactionControlAppendedAfterDml() {
    SqlStatementClassification classification =
        new LexicalSqlStatementClassifier().classify("update demo set enabled = 1; commit;");

    assertEquals(SqlStatementType.UPDATE, classification.primaryType());
    assertTrue(classification.observedTypes().contains(SqlStatementType.COMMIT));
    assertTrue(classification.containsTransactionControl());
  }
}
