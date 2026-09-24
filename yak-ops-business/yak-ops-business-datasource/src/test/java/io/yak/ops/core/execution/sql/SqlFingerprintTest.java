package io.yak.ops.core.execution.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SqlFingerprintTest {

  @Test
  void groupsEquivalentSqlShapesAcrossLiteralValuesAndComments() {
    String left = "select * from users where id = 42 and name = 'Alice' -- comment";
    String right = "SELECT * FROM users WHERE id=99 AND name='Bob'";

    assertEquals(SqlFingerprint.sha256(left), SqlFingerprint.sha256(right));
  }

  @Test
  void redactedPreviewDoesNotExposeCommonLiteralForms() {
    String preview = SqlFingerprint.redactedPreview(
        "select * from secret where token='my-token' and pin=123456 and note=\"private\"",
        2048);

    assertFalse(preview.contains("my-token"));
    assertFalse(preview.contains("123456"));
    assertFalse(preview.contains("private"));
    assertTrue(preview.contains("?"));
  }

  @Test
  void redactsPostgresDollarQuotedBodies() {
    String preview = SqlFingerprint.redactedPreview(
        "select $tag$patient-secret$tag$ as payload",
        2048);

    assertFalse(preview.contains("patient-secret"));
    assertTrue(preview.contains("?"));
  }

  @Test
  void keepsDifferentSqlStructuresDistinct() {
    assertNotEquals(
        SqlFingerprint.sha256("select id from users where id = 1"),
        SqlFingerprint.sha256("delete from users where id = 1"));
  }

  @Test
  void redactedPreviewNeverExceedsMaxChars() {
    String longSql = "select " + String.join(", ", java.util.Collections.nCopies(500, "?"));
    int maxChars = 64;
    String preview = SqlFingerprint.redactedPreview(longSql, maxChars);
    assertTrue(preview.length() <= maxChars,
        "preview length (" + preview.length() + ") must not exceed maxChars (" + maxChars + ")");
    assertTrue(preview.endsWith("…"),
        "truncated preview must end with ellipsis");
  }
}
