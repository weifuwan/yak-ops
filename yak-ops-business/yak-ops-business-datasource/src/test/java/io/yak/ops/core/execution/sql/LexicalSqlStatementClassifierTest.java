package io.yak.ops.core.execution.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LexicalSqlStatementClassifierTest {

  private final SqlStatementClassifier classifier = new LexicalSqlStatementClassifier();

  @Test
  void classifiesSelectAndIgnoresCommentsAndQuotedKeywords() {
    SqlStatementClassification classification = classifier.classify("""
        -- DELETE FROM hidden
        SELECT 'update', "delete", `insert`
        FROM demo
        """);

    assertEquals(SqlStatementType.SELECT, classification.primaryType());
    assertTrue(classification.readOnly());
    assertEquals(1, classification.observedTypes().size());
  }

  @Test
  void classifiesReadOnlyCteByOuterStatement() {
    SqlStatementClassification classification = classifier.classify("""
        WITH recent AS (
          SELECT id FROM orders WHERE created_at > ?
        )
        SELECT * FROM recent
        """);

    assertEquals(SqlStatementType.SELECT, classification.primaryType());
    assertTrue(classification.readOnly());
  }

  @Test
  void detectsMutatingCteEvenWhenOuterStatementIsSelect() {
    SqlStatementClassification classification = classifier.classify("""
        WITH deleted AS (
          DELETE FROM orders WHERE expired = true RETURNING *
        )
        SELECT * FROM deleted
        """);

    assertEquals(SqlStatementType.SELECT, classification.primaryType());
    assertTrue(classification.observedTypes().contains(SqlStatementType.DELETE));
    assertTrue(classification.potentiallyMutating());
    assertFalse(classification.readOnly());
  }

  @Test
  void treatsSelectForUpdateAsNotStrictlyReadOnly() {
    SqlStatementClassification classification =
        classifier.classify("select * from orders where id = ? for update");

    assertEquals(SqlStatementType.SELECT, classification.primaryType());
    assertTrue(classification.observedTypes().contains(SqlStatementType.UPDATE));
    assertFalse(classification.readOnly());
  }

  @Test
  void keepsExplainConservativeForReadOnlyPolicy() {
    SqlStatementClassification classification =
        classifier.classify("explain analyze select * from orders");

    assertEquals(SqlStatementType.EXPLAIN, classification.primaryType());
    assertFalse(classification.readOnly());
  }

  @Test
  void skipsPostgresDollarQuotedProcedureBody() {
    SqlStatementClassification classification = classifier.classify("""
        CREATE FUNCTION cleanup_orders() RETURNS void AS $$
        BEGIN
          DELETE FROM orders WHERE expired = true;
        END;
        $$ LANGUAGE plpgsql
        """);

    assertEquals(SqlStatementType.CREATE, classification.primaryType());
    assertTrue(classification.observedTypes().contains(SqlStatementType.CREATE));
    assertFalse(classification.containsTransactionControl());
  }

  @Test
  void recognizesRuntimeOwnedTransactionControl() {
    assertEquals(SqlStatementType.BEGIN, classifier.classify("begin").primaryType());
    assertEquals(SqlStatementType.BEGIN, classifier.classify("start transaction").primaryType());
    assertEquals(SqlStatementType.COMMIT, classifier.classify("commit").primaryType());
    assertEquals(SqlStatementType.ROLLBACK, classifier.classify("rollback").primaryType());
  }
}
