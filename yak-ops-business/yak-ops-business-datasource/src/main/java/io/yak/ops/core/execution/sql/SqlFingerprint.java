package io.yak.ops.core.execution.sql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Stable, literal-redacted SQL normalization and SHA-256 fingerprinting. */
public final class SqlFingerprint {

  private SqlFingerprint() {}

  public static String sha256(String sql) {
    String normalized = normalize(sql);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(bytes.length * 2);
      for (byte value : bytes) hex.append(String.format(Locale.ROOT, "%02x", value));
      return hex.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  /**
   * Produces a deterministic SQL shape with comments removed and literal values redacted to '?'.
   * It is intended for observability grouping, not SQL parsing or authorization.
   *
   * <p>Double-quoted content is conservatively redacted because dialects disagree on whether it is
   * an identifier or a string literal. Backtick and bracket identifiers are retained.
   */
  public static String normalize(String sql) {
    if (sql == null || sql.isBlank()) return "";
    StringBuilder output = new StringBuilder(sql.length());
    int index = 0;
    while (index < sql.length()) {
      char current = sql.charAt(index);

      if (Character.isWhitespace(current)) {
        index++;
        continue;
      }
      if (current == '-' && index + 1 < sql.length() && sql.charAt(index + 1) == '-') {
        index = skipLineComment(sql, index + 2);
        continue;
      }
      if (current == '/' && index + 1 < sql.length() && sql.charAt(index + 1) == '*') {
        index = skipBlockComment(sql, index + 2);
        continue;
      }
      if (current == '\'') {
        appendToken(output, "?");
        index = skipQuoted(sql, index + 1, '\'', true);
        continue;
      }
      if (current == '"') {
        appendToken(output, "?");
        index = skipQuoted(sql, index + 1, '"', true);
        continue;
      }
      if (current == '$') {
        int delimiterEnd = dollarQuoteDelimiterEnd(sql, index);
        if (delimiterEnd > index) {
          String delimiter = sql.substring(index, delimiterEnd);
          int closing = sql.indexOf(delimiter, delimiterEnd);
          if (closing >= 0) {
            appendToken(output, "?");
            index = closing + delimiter.length();
            continue;
          }
        }
      }
      if (current == '`') {
        int end = skipQuoted(sql, index + 1, current, true);
        appendToken(output, sql.substring(index, Math.min(end, sql.length())));
        index = end;
        continue;
      }
      if (current == '[') {
        int end = sql.indexOf(']', index + 1);
        if (end >= 0) {
          appendToken(output, sql.substring(index, end + 1));
          index = end + 1;
          continue;
        }
      }
      if (isNumberStart(sql, index)) {
        appendToken(output, "?");
        index = skipNumber(sql, index);
        continue;
      }
      if (Character.isLetter(current) || current == '_' || current == '$') {
        int end = index + 1;
        while (end < sql.length()) {
          char value = sql.charAt(end);
          if (!Character.isLetterOrDigit(value) && value != '_' && value != '$') break;
          end++;
        }
        appendToken(output, sql.substring(index, end).toUpperCase(Locale.ROOT));
        index = end;
        continue;
      }
      if (current == '?') {
        appendToken(output, "?");
        index++;
        continue;
      }

      appendToken(output, String.valueOf(current));
      index++;
    }
    return output.toString();
  }

  public static String redactedPreview(String sql, int maxChars) {
    int limit = Math.max(32, maxChars);
    String normalized = normalize(sql);
    if (normalized.length() <= limit) return normalized;
    // Reserve 1 character for the ellipsis so the total length never exceeds maxChars.
    return normalized.substring(0, limit - 1) + "…";
  }

  private static void appendToken(StringBuilder output, String token) {
    if (token == null || token.isEmpty()) return;
    if (!output.isEmpty()) output.append(' ');
    output.append(token);
  }

  private static int skipLineComment(String sql, int index) {
    while (index < sql.length() && sql.charAt(index) != '\n' && sql.charAt(index) != '\r') index++;
    return index;
  }

  private static int skipBlockComment(String sql, int index) {
    int end = sql.indexOf("*/", index);
    return end < 0 ? sql.length() : end + 2;
  }

  private static int skipQuoted(String sql, int index, char quote, boolean doubledEscape) {
    while (index < sql.length()) {
      char value = sql.charAt(index);
      if (value == quote) {
        if (doubledEscape && index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
          index += 2;
          continue;
        }
        return index + 1;
      }
      if (value == '\\' && index + 1 < sql.length()) {
        index += 2;
        continue;
      }
      index++;
    }
    return sql.length();
  }

  private static int dollarQuoteDelimiterEnd(String sql, int index) {
    if (sql.charAt(index) != '$') return -1;
    int cursor = index + 1;
    while (cursor < sql.length()) {
      char value = sql.charAt(cursor);
      if (value == '$') return cursor + 1;
      if (!Character.isLetterOrDigit(value) && value != '_') return -1;
      cursor++;
    }
    return -1;
  }

  private static boolean isNumberStart(String sql, int index) {
    char value = sql.charAt(index);
    if (Character.isDigit(value)) return true;
    return value == '.'
        && index + 1 < sql.length()
        && Character.isDigit(sql.charAt(index + 1));
  }

  private static int skipNumber(String sql, int index) {
    int cursor = index;
    if (cursor + 1 < sql.length()
        && sql.charAt(cursor) == '0'
        && (sql.charAt(cursor + 1) == 'x' || sql.charAt(cursor + 1) == 'X')) {
      cursor += 2;
      while (cursor < sql.length()) {
        char value = sql.charAt(cursor);
        if (!isHexDigit(value) && value != '_') break;
        cursor++;
      }
      return cursor;
    }

    boolean decimalPointSeen = false;
    boolean exponentSeen = false;
    while (cursor < sql.length()) {
      char value = sql.charAt(cursor);
      if (Character.isDigit(value) || value == '_') {
        cursor++;
        continue;
      }
      if (value == '.' && !decimalPointSeen && !exponentSeen) {
        decimalPointSeen = true;
        cursor++;
        continue;
      }
      if ((value == 'e' || value == 'E') && !exponentSeen) {
        exponentSeen = true;
        cursor++;
        if (cursor < sql.length() && (sql.charAt(cursor) == '+' || sql.charAt(cursor) == '-')) {
          cursor++;
        }
        continue;
      }
      break;
    }
    return cursor;
  }

  private static boolean isHexDigit(char value) {
    return Character.isDigit(value)
        || (value >= 'a' && value <= 'f')
        || (value >= 'A' && value <= 'F');
  }
}
